package com.match.registry.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.model.AgentCommandFinishedEvent;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.registry.persistence.ImageDeploymentMapper;
import com.match.registry.persistence.ImageDeploymentRecord;
import com.match.registry.persistence.ImageArtifactMapper;
import com.match.registry.persistence.ImageReleaseMapper;
import com.match.registry.persistence.ImageReleaseRecord;
import com.match.registry.persistence.ImageFileMapper;
import com.match.registry.persistence.ImageFileRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class ImageDeploymentService {
    private static final Pattern DIGEST = Pattern.compile("^sha256:[0-9a-f]{64}$");
    public static final String UPDATE_CONTAINERS = "UPDATE_CONTAINERS";
    public static final String IMAGE_ONLY = "IMAGE_ONLY";
    private final ImageReleaseMapper releases;
    private final ImageDeploymentMapper deployments;
    private final ProcessingAgentMapper agents;
    private final AgentCommandService commands;
    private final AgentImageArchiveService archives;
    private final ImageFileMapper files;
    private final Clock clock;

    @Autowired
    public ImageDeploymentService(ImageReleaseMapper releases, ImageArtifactMapper ignoredArtifacts,
                                  ImageDeploymentMapper deployments, ProcessingAgentMapper agents,
                                  AgentCommandService commands, AgentImageArchiveService archives, ImageFileMapper files, Clock clock) {
        this.releases = releases; this.deployments = deployments; this.agents = agents;
        this.commands = commands; this.archives = archives; this.files = files; this.clock = clock;
    }

    public ImageDeploymentService(ImageReleaseMapper releases, ImageArtifactMapper ignoredArtifacts,
                                  ImageDeploymentMapper deployments, ProcessingAgentMapper agents,
                                  AgentCommandService commands, Clock clock) {
        this(releases, ignoredArtifacts, deployments, agents, commands, null, null, clock);
    }

    public ImageDeploymentService(ImageReleaseMapper releases, ImageArtifactMapper ignoredArtifacts,
                                  ImageDeploymentMapper deployments, ProcessingAgentMapper agents,
                                  AgentCommandService commands, ImageFileMapper files, Clock clock) {
        this(releases, ignoredArtifacts, deployments, agents, commands, null, files, clock);
    }

    public ImageDeploymentService(ImageReleaseMapper releases, ImageArtifactMapper ignoredArtifacts,
                                  ImageDeploymentMapper deployments, ProcessingAgentMapper agents,
                                  AgentCommandService commands, AgentImageArchiveService archives, Clock clock) {
        this(releases, ignoredArtifacts, deployments, agents, commands, archives, null, clock);
    }

    public ImageDeploymentService(ImageReleaseMapper releases, ImageArtifactMapper ignoredArtifacts,
                                  ImageDeploymentMapper deployments, ProcessingAgentMapper agents,
                                  AgentCommandService commands) {
        this(releases, ignoredArtifacts, deployments, agents, commands, Clock.systemUTC());
    }

    @Transactional
    public ImageDeploymentRecord deploy(String role, String releaseId, String agentId, String componentType,
                                        String requestedPolicy, String idempotencyKey, boolean confirmed, int actorId) {
        return deployInternal(role, releaseId, agentId, componentType, requestedPolicy, idempotencyKey,
                confirmed, actorId, null);
    }

    private ImageDeploymentRecord deployInternal(String role, String releaseId, String agentId, String componentType,
                                                 String requestedPolicy, String idempotencyKey, boolean confirmed,
                                                 int actorId, String targetOverride) {
        requireSuperAdmin(role);
        String component = component(componentType);
        String policy = policy(requestedPolicy);
        if (UPDATE_CONTAINERS.equals(policy) && !confirmed) throw new IllegalArgumentException("EXPLICIT_CONFIRMATION_REQUIRED");
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED");
        }
        String key = agentId + ":" + component + ":" + idempotencyKey;
        if (key.length() > 128) throw new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED");
        String slotKey = agentId + ":" + component;
        ImageDeploymentRecord sameRequest = deployments.selectByIdempotencyKeyForUpdate(agentId, component, idempotencyKey);
        if (sameRequest != null) return sameRequest;
        ImageDeploymentRecord active = deployments.selectActiveSlotForUpdate(slotKey);
        if (active != null) {
            if (key.equals(active.getActiveDeploymentKey())) return active;
            // A failed delivery is historical data, not an active deployment. Older
            // records may still carry the slot key, so release that stale lock before
            // accepting a retry while keeping the record for audit/history views.
            if ("FAILED".equals(active.getState()) || "SUCCEEDED".equals(active.getState()) || "ROLLED_BACK".equals(active.getState())) {
                deployments.clearActiveKeys(active.getDeploymentId());
                active = null;
            }
        }
        if (active != null) {
            throw new IllegalArgumentException("DEPLOYMENT_ALREADY_ACTIVE");
        }
        ImageReleaseRecord release = releases.selectById(releaseId);
        if (release == null || !"PUBLISHED".equals(release.getState()) || !component.equals(component(release.getComponentType()))) {
            throw new IllegalArgumentException("RELEASE_NOT_DEPLOYABLE");
        }
        String digest = targetOverride == null ? release.getRegistryDigest() : targetOverride;
        if (digest == null || !DIGEST.matcher(digest).matches()) throw new IllegalArgumentException("IMMUTABLE_DIGEST_REQUIRED");
        ProcessingAgentRecord agent = agents.selectForManagement(agentId);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null) {
            throw new IllegalArgumentException("AGENT_NOT_AVAILABLE");
        }
        ImageDeploymentRecord latest = deployments.selectLatestSucceeded(agentId, component);
        if (latest != null && digest.equals(latest.getTargetDigest())) {
            return latest;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ImageDeploymentRecord deployment = new ImageDeploymentRecord();
        deployment.setDeploymentId(UUID.randomUUID().toString()); deployment.setReleaseId(releaseId);
        deployment.setAgentId(agentId); deployment.setComponentType(component); deployment.setTargetDigest(digest);
        ImageDeploymentRecord prior = deployments.selectLatestSucceeded(agentId, component);
        if (prior != null) deployment.setPreviousDigest(prior.getTargetDigest());
        deployment.setUpdatePolicy(policy); deployment.setState("PENDING"); deployment.setActiveDeploymentKey(key);
        deployment.setActiveAgentComponentKey(slotKey);
        deployment.setIdempotencyKey(idempotencyKey);
        deployment.setRequestedBy(actorId); deployment.setRequestedAt(now); deployment.setUpdatedAt(now);
        try { deployments.insert(deployment); } catch (DuplicateKeyException collision) {
            ImageDeploymentRecord concurrent = deployments.selectActiveSlotForUpdate(slotKey);
            if (concurrent != null && key.equals(concurrent.getActiveDeploymentKey())) return concurrent;
            throw collision;
        }
        AgentCommandView command = commands.requestImageDeploymentCommand(agent, component, digest, policy,
                deployment.getDeploymentId(), idempotencyKey, actorId, role);
        if (command == null) throw new IllegalStateException("DEPLOYMENT_COMMAND_NOT_CREATED");
        deployment.setCommandId(command.getCommandId());
        deployments.updateById(deployment);
        return deployment;
    }

    @Transactional
    public ImageDeploymentRecord rollback(String role, String deploymentId, boolean confirmed, int actorId) {
        requireSuperAdmin(role);
        if (!confirmed) throw new IllegalArgumentException("EXPLICIT_CONFIRMATION_REQUIRED");
        ImageDeploymentRecord failed = deployments.selectById(deploymentId);
        if (failed == null || failed.getPreviousDigest() == null
                || !DIGEST.matcher(failed.getPreviousDigest()).matches()) {
            throw new IllegalArgumentException("ROLLBACK_DIGEST_UNAVAILABLE");
        }
        String key = "rollback-" + deploymentId;
        return deployInternal(role, failed.getReleaseId(), failed.getAgentId(), failed.getComponentType(),
                UPDATE_CONTAINERS, key, true, actorId, failed.getPreviousDigest());
    }

    @Transactional(readOnly = true)
    public ImageDeploymentRecord status(String role, String deploymentId) {
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) throw new IllegalArgumentException("ADMIN_REQUIRED");
        return deployments.selectById(deploymentId);
    }

    @Transactional
    public void reconcile(AgentCommandFinishedEvent event) {
        if (event == null || !AgentCommandService.DEPLOY_IMAGE.equals(event.getCommandType())) return;
        ImageDeploymentRecord deployment = deployments.selectByCommandIdForUpdate(event.getCommandId());
        if (deployment == null || !Objects.equals(event.getAgentId(), deployment.getAgentId())
                || !Objects.equals(event.getCommandId(), deployment.getCommandId())) return;
        if ("SUCCEEDED".equals(deployment.getState()) || "FAILED".equals(deployment.getState())) return;
        String code = event.getResultCode();
        String state;
        boolean terminal = false;
        String current = deployment.getState();
        if ("PULLED".equals(code) && ("PULLED".equals(current) || "RUNNING".equals(current))) return;
        if ("RUNNING".equals(code) && "RUNNING".equals(current)) return;
        if ("PULLED".equals(code) && !"PENDING".equals(current)) return;
        if ("RUNNING".equals(code) && !"PENDING".equals(current) && !"PULLED".equals(current)) return;
        if ("PULLED".equals(code)) state = "PULLED";
        else if ("RUNNING".equals(code)) state = "RUNNING";
        else if (event.isSuccess() && ("SUCCEEDED".equals(code) || "IMAGE_ALREADY_PRESENT".equals(code))) { state = "SUCCEEDED"; terminal = true; }
        else { state = "FAILED"; terminal = true; }
        deployment.setState(state); deployment.setFailureCode(terminal && !event.isSuccess() ? code : null);
        if (terminal && event.isSuccess() && deployment.getFileId() != null && event.getResultJson() != null) {
            try {
                String imageId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(event.getResultJson()).path("imageId").asText();
                if (DIGEST.matcher(imageId).matches()) deployment.setTargetDigest(imageId);
            } catch (java.io.IOException ignored) { /* Older Agents may omit the inspected image identity. */ }
        }
        deployment.setFailureMessage(terminal && !event.isSuccess() ? event.getResultMessage() : null);
        deployment.setUpdatedAt(LocalDateTime.now(clock));
        if (terminal) { deployment.setCompletedAt(deployment.getUpdatedAt()); deployment.setActiveDeploymentKey(null); deployment.setActiveAgentComponentKey(null); }
        deployments.updateById(deployment);
        if (terminal) deployments.clearActiveKeys(deployment.getDeploymentId());
    }

    @Transactional
    public void recoverTerminalCommand(String commandId, String terminalState, String failureCode, String failureMessage) {
        if (!"FAILED".equals(terminalState) && !"SUCCEEDED".equals(terminalState)) return;
        ImageDeploymentRecord deployment = deployments.selectByCommandIdForUpdate(commandId);
        if (deployment == null || "SUCCEEDED".equals(deployment.getState()) || "FAILED".equals(deployment.getState())) return;
        deployment.setState(terminalState); deployment.setFailureCode(failureCode);
        deployment.setFailureMessage(failureMessage); deployment.setCompletedAt(LocalDateTime.now(clock));
        deployment.setUpdatedAt(deployment.getCompletedAt()); deployment.setActiveDeploymentKey(null); deployment.setActiveAgentComponentKey(null); deployments.updateById(deployment);
        deployments.clearActiveKeys(deployment.getDeploymentId());
    }

    @Transactional
    public ImageDeploymentRecord deployFile(String role, String fileId, String agentId, boolean overwrite,
                                            String idempotencyKey, int actorId) {
        requireSuperAdmin(role);
        if (files == null) throw new IllegalStateException("IMAGE_FILE_SERVICE_UNAVAILABLE");
        ImageFileRecord file = files.selectById(fileId);
        if (file == null || !Boolean.TRUE.equals(file.getEnabled())) throw new IllegalArgumentException("IMAGE_FILE_NOT_ENABLED");
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty() || idempotencyKey.length() > 128)
            throw new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED");
        ProcessingAgentRecord agent = agents.selectForManagement(agentId);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null)
            throw new IllegalArgumentException("AGENT_NOT_AVAILABLE");
        String imageName = file.getImageRepository() + ":" + file.getImageTag();
        String slotKey = agentId + ":DIRECT";
        ImageDeploymentRecord active = deployments.selectActiveSlotForUpdate(slotKey);
        if (active != null) {
            String activeImage = active.getTargetImage();
            String server = agent.getDisplayName() == null || agent.getDisplayName().trim().isEmpty()
                    ? agentId : agent.getDisplayName();
            String detail = activeImage == null || activeImage.trim().isEmpty()
                    ? "已有镜像下发任务正在执行"
                    : "正在下发 " + activeImage;
            throw new IllegalArgumentException(server + " " + detail + "，请等待当前任务完成后再下发其他镜像");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ImageDeploymentRecord deployment = new ImageDeploymentRecord();
        deployment.setDeploymentId(UUID.randomUUID().toString()); deployment.setFileId(fileId); deployment.setAgentId(agentId);
        deployment.setComponentType("DIRECT"); deployment.setTargetImage(imageName); deployment.setUpdatePolicy("IMAGE_ONLY");
        deployment.setState("PENDING"); deployment.setActiveDeploymentKey(agentId + ":DIRECT:" + idempotencyKey);
        deployment.setActiveAgentComponentKey(slotKey); deployment.setIdempotencyKey(idempotencyKey);
        deployment.setRequestedBy(actorId); deployment.setRequestedAt(now); deployment.setUpdatedAt(now); deployments.insert(deployment);
        AgentCommandView command = commands.requestImageFileDeploymentCommand(agent, imageName, overwrite,
                deployment.getDeploymentId(), idempotencyKey, actorId, role);
        deployment.setCommandId(command.getCommandId()); deployments.updateById(deployment); return deployment;
    }

    @Transactional
    public void deleteFailed(String role, String deploymentId) {
        requireSuperAdmin(role);
        ImageDeploymentRecord deployment = deployments.selectById(deploymentId);
        if (deployment == null) throw new IllegalArgumentException("推送任务不存在");
        if (!"FAILED".equals(deployment.getState())) throw new IllegalArgumentException("仅能删除失败的推送任务");
        deployments.clearActiveKeys(deploymentId); deployments.deleteById(deploymentId);
    }

    @Scheduled(initialDelayString = "${xkp.registry.deployment-timeout-initial-ms:10000}",
            fixedDelayString = "${xkp.registry.deployment-timeout-scan-ms:60000}")
    @Transactional
    public void failStaleDeployments() {
        // Use request time so repeated progress cannot keep a stuck transfer alive.
        // Agent cannot keep the platform queue occupied by repeatedly reporting progress.
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(195);
        for (ImageDeploymentRecord deployment : deployments.selectStaleRunning(cutoff, 50)) {
            if (deployment.getCommandId() != null
                    && commands.failStaleImageDeployment(deployment.getCommandId(), cutoff)) {
                recoverTerminalCommand(deployment.getCommandId(), "FAILED",
                        "IMAGE_DEPLOYMENT_TIMEOUT", "镜像推送超过 3 小时 15 分钟未完成，已释放服务器命令队列");
            }
        }
    }

    private void requireSuperAdmin(String role) { if (!"SUPER_ADMIN".equals(role)) throw new IllegalArgumentException("SUPER_ADMIN_REQUIRED"); }
    private String policy(String value) {
        String normalized = value == null ? UPDATE_CONTAINERS : value.toUpperCase(Locale.ROOT);
        if (!UPDATE_CONTAINERS.equals(normalized) && !IMAGE_ONLY.equals(normalized)) throw new IllegalArgumentException("INVALID_UPDATE_POLICY");
        return normalized;
    }
    private String component(String value) {
        String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
        if (!"ANNOTATION".equals(normalized) && !"EDITOR".equals(normalized)) throw new IllegalArgumentException("INVALID_COMPONENT");
        return normalized;
    }
}
