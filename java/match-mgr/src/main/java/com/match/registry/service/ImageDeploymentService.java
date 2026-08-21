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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final Clock clock;

    @Autowired
    public ImageDeploymentService(ImageReleaseMapper releases, ImageArtifactMapper ignoredArtifacts,
                                  ImageDeploymentMapper deployments, ProcessingAgentMapper agents,
                                  AgentCommandService commands, Clock clock) {
        this.releases = releases; this.deployments = deployments; this.agents = agents;
        this.commands = commands; this.clock = clock;
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
        ImageDeploymentRecord active = deployments.selectActiveSlotForUpdate(slotKey);
        if (active != null) {
            if (key.equals(active.getActiveDeploymentKey())) return active;
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
        LocalDateTime now = LocalDateTime.now(clock);
        ImageDeploymentRecord deployment = new ImageDeploymentRecord();
        deployment.setDeploymentId(UUID.randomUUID().toString()); deployment.setReleaseId(releaseId);
        deployment.setAgentId(agentId); deployment.setComponentType(component); deployment.setTargetDigest(digest);
        ImageDeploymentRecord prior = deployments.selectLatestSucceeded(agentId, component);
        if (prior != null) deployment.setPreviousDigest(prior.getTargetDigest());
        deployment.setUpdatePolicy(policy); deployment.setState("PENDING"); deployment.setActiveDeploymentKey(key);
        deployment.setActiveAgentComponentKey(slotKey);
        deployment.setRequestedBy(actorId); deployment.setRequestedAt(now); deployment.setUpdatedAt(now);
        try { deployments.insert(deployment); } catch (DuplicateKeyException collision) {
            ImageDeploymentRecord concurrent = deployments.selectActiveSlotForUpdate(slotKey);
            if (concurrent != null && key.equals(concurrent.getActiveDeploymentKey())) return concurrent;
            throw collision;
        }
        AgentCommandView command = commands.requestImageDeploymentCommand(agent, component, digest, policy,
                deployment.getDeploymentId(), key, actorId, role);
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
        String key = "rollback:" + deploymentId;
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
        if (deployment == null || !event.getAgentId().equals(deployment.getAgentId())
                || !Objects.equals(event.getCommandId(), deployment.getCommandId())) return;
        String code = event.getResultCode();
        String state;
        boolean terminal = false;
        if ("PULLED".equals(code)) state = "PULLED";
        else if ("RUNNING".equals(code)) state = "RUNNING";
        else if (event.isSuccess() && "SUCCEEDED".equals(code)) { state = "SUCCEEDED"; terminal = true; }
        else { state = "FAILED"; terminal = true; }
        deployment.setState(state); deployment.setFailureCode(terminal && !event.isSuccess() ? code : null);
        deployment.setFailureMessage(terminal && !event.isSuccess() ? event.getResultMessage() : null);
        deployment.setUpdatedAt(LocalDateTime.now(clock));
        if (terminal) { deployment.setCompletedAt(deployment.getUpdatedAt()); deployment.setActiveDeploymentKey(null); deployment.setActiveAgentComponentKey(null); }
        deployments.updateById(deployment);
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
