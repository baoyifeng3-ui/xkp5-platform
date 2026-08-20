package com.match.mode.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentAuditService;
import com.match.agent.service.AgentCommandService;
import com.match.entity.User;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentOperationRecord;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.environment.service.EnvironmentCommandFactory;
import com.match.mode.model.ModeTransitionStepView;
import com.match.mode.model.ModeTransitionView;
import com.match.mode.persistence.ModeTrainingSnapshotMapper;
import com.match.mode.persistence.ModeTrainingSnapshotRecord;
import com.match.mode.persistence.ModeTransitionMapper;
import com.match.mode.persistence.ModeTransitionRecord;
import com.match.mode.persistence.ModeTransitionStepMapper;
import com.match.mode.persistence.ModeTransitionStepRecord;
import com.match.mode.persistence.ProcessingAgentModeMapper;
import com.match.mode.persistence.ProcessingAgentModeRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;

/** Durable per-Agent entry orchestration. Container work is always phase-barriered. */
@Service
public class ModeTransitionService {
    private static final long ONLINE_TIMEOUT_SECONDS = 300L;
    private static final String NORMAL = "NORMAL";
    private static final String ENTERING = "ENTERING_COMPETITION";
    private static final String COMPETITION_MODE = "COMPETITION";
    private static final String TRAINING_MODE = "TRAINING";

    private final ProcessingAgentModeMapper modeMapper;
    private final ModeTransitionMapper transitionMapper;
    private final ModeTransitionStepMapper stepMapper;
    private final ModeTrainingSnapshotMapper snapshotMapper;
    private final ProcessingAgentMapper agentMapper;
    private final TrainingEnvironmentMapper trainingMapper;
    private final CompetitionEnvironmentMapper competitionMapper;
    private final ProcessingEnvironmentSlotMapper slotMapper;
    private final EnvironmentOperationMapper operationMapper;
    private final AgentCommandService commandService;
    private final EnvironmentCommandFactory commandFactory;
    private final AgentAuditService auditService;
    private final Clock clock;
    private final ObjectMapper verificationMapper;

    public ModeTransitionService(ProcessingAgentModeMapper modeMapper,
                                 ModeTransitionMapper transitionMapper,
                                 ModeTransitionStepMapper stepMapper,
                                 ModeTrainingSnapshotMapper snapshotMapper,
                                 ProcessingAgentMapper agentMapper,
                                 TrainingEnvironmentMapper trainingMapper,
                                 CompetitionEnvironmentMapper competitionMapper,
                                 ProcessingEnvironmentSlotMapper slotMapper,
                                 EnvironmentOperationMapper operationMapper,
                                 AgentCommandService commandService,
                                 EnvironmentCommandFactory commandFactory,
                                 AgentAuditService auditService,
                                 Clock clock) {
        this.modeMapper = modeMapper;
        this.transitionMapper = transitionMapper;
        this.stepMapper = stepMapper;
        this.snapshotMapper = snapshotMapper;
        this.agentMapper = agentMapper;
        this.trainingMapper = trainingMapper;
        this.competitionMapper = competitionMapper;
        this.slotMapper = slotMapper;
        this.operationMapper = operationMapper;
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.auditService = auditService;
        this.clock = clock;
        this.verificationMapper = new ObjectMapper();
    }

    @Transactional
    public ModeTransitionView planEntry(String agentId, User actor) {
        ActorIdentity identity = requireAdmin(actor);
        requireAgentId(agentId);
        ProcessingAgentRecord agent = agentMapper.selectForManagement(agentId);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null) {
            throw new IllegalArgumentException("处理服务器不可用");
        }

        ProcessingAgentModeRecord mode = lockOrCreateMode(agentId);
        ModeTransitionRecord active = transitionMapper.selectActiveForUpdate(agentId);
        if (active != null) {
            if (COMPETITION_MODE.equals(active.getTargetMode())) {
                return view(active, safeSteps(active.getTransitionId()));
            }
            throw new ModeConflictException("AGENT_MODE_TRANSITION_CONFLICT",
                    "Processing Agent has an active transition for another target");
        }
        if (!TRAINING_MODE.equals(mode.getDesiredMode()) || !NORMAL.equals(mode.getActualMode())
                || mode.getActiveTransitionId() != null) {
            throw new ModeConflictException("AGENT_MODE_NOT_IDLE",
                    "Processing Agent is not idle in training mode");
        }

        LocalDateTime now = utcNow();
        ModeTransitionRecord transition = new ModeTransitionRecord();
        transition.setTransitionId(UUID.randomUUID().toString());
        transition.setAgentId(agentId);
        transition.setSourceMode(TRAINING_MODE);
        transition.setTargetMode(COMPETITION_MODE);
        transition.setState("RUNNING");
        transition.setActiveTransitionKey(agentId + ":COMPETITION");
        transition.setActorUserId(identity.userId);
        transition.setActorRole(identity.role);
        transition.setRequestedAt(now);
        transition.setUpdatedAt(now);
        transitionMapper.insert(transition);

        List<TrainingEnvironmentRecord> environments = safeTraining(agentId);
        String preflightFailure = preflightFailure(agent, environments);
        if (preflightFailure != null) {
            degrade(transition, mode, preflightFailure, now);
            auditService.recordFailure("MODE_ENTRY_DEGRADED", preflightFailure, identity.userId,
                    agentId, transition.getTransitionId());
            return view(transition, Collections.<ModeTransitionStepRecord>emptyList());
        }

        List<ModeTransitionStepRecord> created = new ArrayList<>();
        int ordinal = 1;
        for (TrainingEnvironmentRecord environment : environments) {
            if (!"RUNNING".equals(environment.getActualState())) {
                continue;
            }
            ModeTrainingSnapshotRecord snapshot = new ModeTrainingSnapshotRecord();
            snapshot.setTransitionId(transition.getTransitionId());
            snapshot.setEnvironmentId(environment.getEnvironmentId());
            snapshot.setCapturedAt(now);
            snapshotMapper.insert(snapshot);
            created.add(step(transition, 1, ordinal++, "TRAINING", environment.getEnvironmentId(),
                    "STOP_TRAINING_ENVIRONMENT"));
        }

        Set<String> boundSlots = boundSlotIds(agentId);
        List<CompetitionEnvironmentRecord> candidates = safeCompetition(agentId);
        ordinal = 1;
        for (CompetitionEnvironmentRecord environment : candidates) {
            if (boundSlots.contains(environment.getSlotId()) && ready(environment)) {
                created.add(step(transition, 2, ordinal++, "COMPETITION", environment.getEnvironmentId(),
                        "START_COMPETITION_ENVIRONMENT"));
            }
        }

        transition.setState("RUNNING");
        mode.setDesiredMode(COMPETITION_MODE);
        mode.setActualMode(ENTERING);
        mode.setActiveTransitionId(transition.getTransitionId());
        modeMapper.updateTransition(agentId, COMPETITION_MODE, ENTERING,
                transition.getTransitionId(), now);
        auditService.recordSuccess("MODE_ENTRY_STARTED", identity.userId, agentId,
                transition.getTransitionId());
        if (created.isEmpty()) {
            transition.setState("SUCCEEDED");
            transition.setCompletedAt(now);
            transition.setUpdatedAt(now);
            transition.setActiveTransitionKey(null);
            transitionMapper.updateTerminal(transition.getTransitionId(), "SUCCEEDED", null, now);
            mode.setActualMode(COMPETITION_MODE);
            mode.setActiveTransitionId(null);
            modeMapper.updateTransition(agentId, COMPETITION_MODE, COMPETITION_MODE, null, now);
            auditService.recordSuccess("MODE_ENTRY_COMPLETED", identity.userId, agentId,
                    transition.getTransitionId());
            return view(transition, created);
        }
        dispatchAfterCommit(transition, created, agent, identity);
        return view(transition, created);
    }

    /** Convenience overload used by coordinators that already resolved actor identity. */
    @Transactional
    public ModeTransitionView planEntry(String agentId, int actorUserId, String actorRole) {
        User actor = new User();
        actor.setUserId(actorUserId);
        actor.setEnabled(true);
        actor.setRole(actorRole);
        return planEntry(agentId, actor);
    }

    @Transactional(readOnly = true)
    public ModeTransitionView get(String transitionId) {
        ModeTransitionRecord transition = transitionMapper.selectForUpdate(transitionId);
        if (transition == null) {
            throw new IllegalArgumentException("模式切换不存在");
        }
        return view(transition, safeSteps(transitionId));
    }

    /** Re-dispatches the lowest phase that is not terminal; phase 2 never bypasses phase 1. */
    @Transactional
    public void dispatchReadyPhase(String transitionId) {
        ModeTransitionRecord transition = transitionMapper.selectForUpdate(transitionId);
        if (transition == null || !COMPETITION_MODE.equals(transition.getTargetMode())
                || "SUCCEEDED".equals(transition.getState())) {
            return;
        }
        ProcessingAgentRecord agent = agentMapper.selectForManagement(transition.getAgentId());
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled())) {
            transitionMapper.updateState(transitionId, "DEGRADED", "AGENT_OFFLINE", utcNow());
            return;
        }
        ActorIdentity actor = new ActorIdentity(transition.getActorUserId(), transition.getActorRole());
        List<ModeTransitionStepRecord> all = safeSteps(transitionId);
        dispatchReady(transition, all, agent, actor);
    }

    @Transactional
    public void convergeAll(String targetMode, int actorUserId, String actorRole) {
        if (!COMPETITION_MODE.equals(targetMode)) {
            return;
        }
        List<ProcessingAgentRecord> agents = agentMapper.selectVisibleAgents();
        if (agents == null) {
            return;
        }
        for (ProcessingAgentRecord agent : agents) {
            try {
                planEntry(agent.getAgentId(), actorUserId, actorRole);
            } catch (RuntimeException failure) {
                if (agent.getAgentId() != null) {
                    auditService.recordFailure("MODE_ENTRY_DEGRADED", "AGENT_TRANSITION_ERROR",
                            actorUserId, agent.getAgentId(), null);
                }
            }
        }
    }

    private void dispatchAfterCommit(final ModeTransitionRecord transition,
                                     final List<ModeTransitionStepRecord> created,
                                     final ProcessingAgentRecord agent,
                                     final ActorIdentity actor) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchReadyPhase(transition.getTransitionId());
                }
            });
            return;
        }
        dispatchReady(transition, created, agent, actor);
    }

    private void dispatchReady(ModeTransitionRecord transition,
                               List<ModeTransitionStepRecord> steps,
                               ProcessingAgentRecord agent,
                               ActorIdentity actor) {
        if (steps == null) {
            return;
        }
        int phase = lowestNonTerminalPhase(steps);
        if (phase == 0) {
            return;
        }
        for (ModeTransitionStepRecord step : steps) {
            if (step.getPhaseNumber() == null || step.getPhaseNumber() != phase
                    || !"PENDING".equals(step.getState())) {
                continue;
            }
            AgentCommandView command;
            try {
                String payload = payload(step, agent);
                command = commandService.requestEnvironmentCommand(agent,
                        step.getActionType(), payload, actor.userId, actor.role,
                        step.getIdempotencyKey());
            } catch (RuntimeException failure) {
                step.setState("FAILED");
                step.setResultCode("COMMAND_DISPATCH_FAILED");
                step.setResultMessage(bounded(failure.getMessage()));
                if (transition != null) {
                    transition.setState("DEGRADED");
                    transition.setFailureSummary("COMMAND_DISPATCH_FAILED");
                    transitionMapper.updateState(transition.getTransitionId(), "DEGRADED",
                            "COMMAND_DISPATCH_FAILED", utcNow());
                }
                continue;
            }
            if (command == null || command.getCommandId() == null) {
                step.setState("FAILED");
                step.setResultCode("COMMAND_DISPATCH_FAILED");
                if (transition != null) {
                    transition.setState("DEGRADED");
                    transition.setFailureSummary("COMMAND_DISPATCH_FAILED");
                    transitionMapper.updateState(transition.getTransitionId(), "DEGRADED",
                            "COMMAND_DISPATCH_FAILED", utcNow());
                }
                continue;
            }
            step.setCommandId(command.getCommandId());
            step.setState("DISPATCHED");
            stepMapper.markDispatched(step.getStepId(), command.getCommandId(), utcNow());
        }
    }

    private String payload(ModeTransitionStepRecord step, ProcessingAgentRecord agent) {
        if ("TRAINING".equals(step.getEnvironmentKind())) {
            TrainingEnvironmentRecord environment = trainingMapper.selectForUpdate(step.getEnvironmentId());
            if (environment == null) {
                throw new IllegalArgumentException("实训环境不存在");
            }
            return commandFactory.createPayloadJson(environment, step.getStepId());
        }
        CompetitionEnvironmentRecord environment = competitionMapper.selectForUpdate(step.getEnvironmentId());
        if (environment == null) {
            throw new IllegalArgumentException("比赛环境不存在");
        }
        return commandFactory.createPayloadJson(environment, step.getStepId());
    }

    private ModeTransitionStepRecord step(ModeTransitionRecord transition, int phase, int ordinal,
                                          String kind, String environmentId, String action) {
        ModeTransitionStepRecord result = new ModeTransitionStepRecord();
        result.setStepId(UUID.randomUUID().toString());
        result.setTransitionId(transition.getTransitionId());
        result.setPhaseNumber(phase);
        result.setStepOrdinal(ordinal);
        result.setEnvironmentKind(kind);
        result.setEnvironmentId(environmentId);
        result.setActionType(action);
        result.setState("PENDING");
        result.setIdempotencyKey("mode:" + transition.getTransitionId() + ":" + phase + ":"
                + environmentId + ":" + action);
        result.setUpdatedAt(utcNow());
        stepMapper.insert(result);
        return result;
    }

    private ProcessingAgentModeRecord lockOrCreateMode(String agentId) {
        ProcessingAgentModeRecord mode = modeMapper.selectForUpdate(agentId);
        if (mode != null) {
            return mode;
        }
        mode = new ProcessingAgentModeRecord();
        mode.setAgentId(agentId);
        mode.setDesiredMode(TRAINING_MODE);
        mode.setActualMode(NORMAL);
        mode.setLockVersion(0L);
        mode.setUpdatedAt(utcNow());
        modeMapper.insert(mode);
        return mode;
    }

    private String preflightFailure(ProcessingAgentRecord agent,
                                    List<TrainingEnvironmentRecord> environments) {
        if (agent.getLastSeenAt() == null
                || agent.getLastSeenAt().isBefore(utcNow().minusSeconds(ONLINE_TIMEOUT_SECONDS))) {
            return "AGENT_OFFLINE";
        }
        for (TrainingEnvironmentRecord environment : environments) {
            EnvironmentOperationRecord active = operationMapper.selectActive(environment.getEnvironmentId());
            if (active != null) {
                return "ENVIRONMENT_OPERATION_ACTIVE";
            }
        }
        for (CompetitionEnvironmentRecord environment : safeCompetition(agent.getAgentId())) {
            EnvironmentOperationRecord active = operationMapper.selectActive(environment.getEnvironmentId());
            if (active != null) {
                return "ENVIRONMENT_OPERATION_ACTIVE";
            }
        }
        return null;
    }

    private Set<String> boundSlotIds(String agentId) {
        Set<String> result = new HashSet<>();
        List<ProcessingEnvironmentSlotRecord> slots = slotMapper.selectByAgentForUpdate(agentId);
        if (slots != null) {
            for (ProcessingEnvironmentSlotRecord slot : slots) {
                if (slot != null && slot.getUserId() != null && slot.getSlotId() != null) {
                    result.add(slot.getSlotId());
                }
            }
        }
        return result;
    }

    private boolean ready(CompetitionEnvironmentRecord environment) {
        String shortAgent = environment == null || environment.getAgentId() == null
                ? "" : environment.getAgentId().length() >= 8
                ? environment.getAgentId().substring(0, 8) : "";
        String prefix = "xkp-comp-" + shortAgent + "-s"
                + (environment == null ? "" : environment.getSlotNumber());
        return environment != null && environment.getEnvironmentId() != null
                && environment.getSlotId() != null && "STOPPED".equals(environment.getDesiredState())
                && "STOPPED".equals(environment.getActualState())
                && environment.getCurrentOperationId() == null
                && "STOPPED".equals(environment.getAnnotationContainerState())
                && "STOPPED".equals(environment.getEditorContainerState())
                && Objects.equals("competition/slot-" + environment.getSlotNumber(),
                environment.getWorkspaceRelativePath())
                && Objects.equals(prefix + "-annotation", environment.getAnnotationContainerName())
                && Objects.equals(prefix + "-editor", environment.getEditorContainerName())
                && nonBlank(environment.getAnnotationConfigFingerprint())
                && nonBlank(environment.getEditorConfigFingerprint())
                && nonBlank(environment.getAnnotationTemplateId())
                && environment.getAnnotationTemplateVersion() != null
                && environment.getAnnotationTemplateVersion() >= 1
                && nonBlank(environment.getEditorTemplateId())
                && environment.getEditorTemplateVersion() != null
                && environment.getEditorTemplateVersion() >= 1
                && verified(environment);
    }

    private boolean verified(CompetitionEnvironmentRecord environment) {
        if (environment.getLastVerifiedAt() == null || !nonBlank(environment.getLastComponentResultsJson())
                || environment.getLastComponentResultsJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > 16 * 1024) {
            return false;
        }
        try (JsonParser parser = verificationMapper.getFactory().createParser(
                environment.getLastComponentResultsJson())) {
            JsonNode root = verificationMapper.readTree(parser);
            if (parser.nextToken() != null || root == null || !root.isObject()
                    || root.size() != 1 || !root.has("pair")) {
                return false;
            }
            JsonNode pair = root.get("pair");
            JsonNode annotation = pair == null ? null : pair.get("annotation");
            JsonNode editor = pair == null ? null : pair.get("editor");
            return pair != null && pair.isObject() && pair.size() == 2
                    && annotation != null && editor != null
                    && annotation.isObject() && editor.isObject()
                    && annotation.size() == 4 && editor.size() == 4
                    && exact(annotation, "componentType", "ANNOTATION")
                    && exact(editor, "componentType", "EDITOR")
                    && exact(annotation, "containerName", environment.getAnnotationContainerName())
                    && exact(editor, "containerName", environment.getEditorContainerName())
                    && exact(annotation, "configFingerprint", environment.getAnnotationConfigFingerprint())
                    && exact(editor, "configFingerprint", environment.getEditorConfigFingerprint())
                    && exact(annotation, "state", "STOPPED")
                    && exact(editor, "state", "STOPPED");
        } catch (Exception invalidVerification) {
            return false;
        }
    }

    private boolean exact(JsonNode node, String field, String expected) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && Objects.equals(expected, value.asText());
    }

    private List<TrainingEnvironmentRecord> safeTraining(String agentId) {
        List<TrainingEnvironmentRecord> result = trainingMapper.selectByAgentForUpdate(agentId);
        return result == null ? Collections.<TrainingEnvironmentRecord>emptyList() : result;
    }

    private List<CompetitionEnvironmentRecord> safeCompetition(String agentId) {
        List<CompetitionEnvironmentRecord> result = competitionMapper.selectByAgent(agentId);
        return result == null ? Collections.<CompetitionEnvironmentRecord>emptyList() : result;
    }

    private List<ModeTransitionStepRecord> safeSteps(String transitionId) {
        List<ModeTransitionStepRecord> result = stepMapper.selectByTransition(transitionId);
        return result == null ? Collections.<ModeTransitionStepRecord>emptyList() : result;
    }

    private int lowestNonTerminalPhase(List<ModeTransitionStepRecord> steps) {
        int phase = Integer.MAX_VALUE;
        for (ModeTransitionStepRecord step : steps) {
            if (!terminal(step.getState()) && step.getPhaseNumber() != null) {
                phase = Math.min(phase, step.getPhaseNumber());
            }
        }
        if (phase == Integer.MAX_VALUE) {
            return 0;
        }
        for (ModeTransitionStepRecord later : steps) {
            if (later.getPhaseNumber() != null && later.getPhaseNumber() > phase
                    && !terminal(later.getState())) {
                for (ModeTransitionStepRecord previous : steps) {
                    if (previous.getPhaseNumber() != null
                            && previous.getPhaseNumber() < later.getPhaseNumber()
                            && "FAILED".equals(previous.getState())) {
                        return 0;
                    }
                }
            }
        }
        return phase;
    }

    private boolean terminal(String state) {
        return "SUCCEEDED".equals(state) || "FAILED".equals(state);
    }

    private void degrade(ModeTransitionRecord transition, ProcessingAgentModeRecord mode,
                         String reason, LocalDateTime now) {
        transition.setState("DEGRADED");
        transition.setFailureSummary(reason);
        transition.setUpdatedAt(now);
        transitionMapper.updateState(transition.getTransitionId(), "DEGRADED", reason, now);
        mode.setDesiredMode(COMPETITION_MODE);
        mode.setActualMode("DEGRADED");
        mode.setActiveTransitionId(transition.getTransitionId());
        modeMapper.updateTransition(mode.getAgentId(), COMPETITION_MODE, "DEGRADED",
                transition.getTransitionId(), now);
    }

    private ModeTransitionView view(ModeTransitionRecord transition,
                                    List<ModeTransitionStepRecord> steps) {
        ModeTransitionView result = new ModeTransitionView();
        result.setTransitionId(transition.getTransitionId());
        result.setAgentId(transition.getAgentId());
        result.setSourceMode(transition.getSourceMode());
        result.setTargetMode(transition.getTargetMode());
        result.setState(transition.getState());
        result.setFailureSummary(transition.getFailureSummary());
        result.setRequestedAt(transition.getRequestedAt());
        result.setCompletedAt(transition.getCompletedAt());
        List<ModeTransitionStepRecord> ordered = new ArrayList<>(steps);
        Collections.sort(ordered, Comparator.comparing(ModeTransitionStepRecord::getPhaseNumber)
                .thenComparing(ModeTransitionStepRecord::getStepOrdinal));
        for (ModeTransitionStepRecord step : ordered) {
            ModeTransitionStepView item = new ModeTransitionStepView();
            item.setStepId(step.getStepId());
            item.setPhaseNumber(step.getPhaseNumber());
            item.setStepOrdinal(step.getStepOrdinal());
            item.setEnvironmentKind(step.getEnvironmentKind());
            item.setEnvironmentId(step.getEnvironmentId());
            item.setActionType(step.getActionType());
            item.setState(step.getState());
            item.setResultCode(step.getResultCode());
            item.setResultMessage(step.getResultMessage());
            result.getSteps().add(item);
        }
        return result;
    }

    private ActorIdentity requireAdmin(User actor) {
        if (actor == null || !Boolean.TRUE.equals(actor.getEnabled()) || actor.getUserId() == null
                || !"ADMIN".equals(actor.getRole())) {
            throw new IllegalArgumentException("仅普通管理员可以执行模式切换");
        }
        return new ActorIdentity(actor.getUserId(), "ADMIN");
    }

    private void requireAgentId(String agentId) {
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new IllegalArgumentException("处理服务器编号不能为空");
        }
    }

    private boolean nonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String bounded(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static final class ActorIdentity {
        private final int userId;
        private final String role;

        private ActorIdentity(Integer userId, String role) {
            this.userId = userId == null ? 0 : userId;
            this.role = role == null ? "ADMIN" : role;
        }
    }
}
