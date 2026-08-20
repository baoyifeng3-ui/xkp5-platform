package com.match.mode.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;

/** Durable per-Agent entry orchestration. Container work is always phase-barriered. */
@Service
public class ModeTransitionService {
    private static final long ONLINE_TIMEOUT_SECONDS = 300L;
    private static final String NORMAL = "NORMAL";
    private static final String ENTERING = "ENTERING_COMPETITION";
    private static final String EXITING = "EXITING_COMPETITION";
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
    private final ModeTransitionDispatchCoordinator dispatcher;
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
        this(modeMapper, transitionMapper, stepMapper, snapshotMapper, agentMapper,
                trainingMapper, competitionMapper, slotMapper, operationMapper, commandService,
                commandFactory, auditService, clock,
                new ModeTransitionDispatchCoordinator(new ModeTransitionDispatchWorker(
                        transitionMapper, stepMapper, modeMapper, agentMapper, trainingMapper,
                        competitionMapper, commandService, commandFactory, clock)));
    }

    @Autowired
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
                                 Clock clock,
                                 ModeTransitionDispatchCoordinator dispatcher) {
        this.modeMapper = modeMapper;
        this.transitionMapper = transitionMapper;
        this.stepMapper = stepMapper;
        this.snapshotMapper = snapshotMapper;
        this.agentMapper = agentMapper;
        this.trainingMapper = trainingMapper;
        this.competitionMapper = competitionMapper;
        this.slotMapper = slotMapper;
        this.operationMapper = operationMapper;
        this.dispatcher = dispatcher;
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

        List<ModeTransitionStepRecord> created = materializeEntrySteps(transition, environments, now);

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

    @Transactional
    public ModeTransitionView planExit(String agentId, User actor) {
        ActorIdentity identity = requireAdmin(actor);
        requireAgentId(agentId);
        ProcessingAgentRecord agent = agentMapper.selectForManagement(agentId);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null) {
            throw new IllegalArgumentException("处理服务器不可用");
        }
        ProcessingAgentModeRecord mode = lockOrCreateMode(agentId);
        ModeTransitionRecord active = transitionMapper.selectActiveForUpdate(agentId);
        if (active != null) {
            if (TRAINING_MODE.equals(active.getTargetMode())) {
                return view(active, safeSteps(active.getTransitionId()));
            }
            throw new ModeConflictException("AGENT_MODE_TRANSITION_CONFLICT",
                    "Processing Agent has an active transition for another target");
        }
        if (!COMPETITION_MODE.equals(mode.getDesiredMode())
                || !COMPETITION_MODE.equals(mode.getActualMode())) {
            throw new ModeConflictException("AGENT_MODE_NOT_IDLE",
                    "Processing Agent is not idle in competition mode");
        }

        ModeTransitionRecord entry = transitionMapper.selectLatestCompetitionForAgent(agentId);
        if (entry == null) {
            throw new ModeConflictException("COMPETITION_ENTRY_NOT_FOUND",
                    "Competition entry transition is missing");
        }
        LocalDateTime now = utcNow();
        ModeTransitionRecord transition = transition(agentId, COMPETITION_MODE, TRAINING_MODE,
                identity, now);
        transitionMapper.insert(transition);

        List<ModeTransitionStepRecord> created = new ArrayList<>();
        Set<String> frozenCompetition = new HashSet<>();
        int ordinal = 1;
        for (ModeTransitionStepRecord entryStep : safeSteps(entry.getTransitionId())) {
            if ("COMPETITION".equals(entryStep.getEnvironmentKind())
                    && entryStep.getEnvironmentId() != null
                    && frozenCompetition.add(entryStep.getEnvironmentId())) {
                created.add(step(transition, 1, ordinal++, "COMPETITION",
                        entryStep.getEnvironmentId(), "STOP_COMPETITION_ENVIRONMENT"));
            }
        }
        ordinal = 1;
        Set<String> frozenTraining = new HashSet<>();
        List<ModeTrainingSnapshotRecord> entrySnapshots = snapshotMapper.selectByTransition(
                entry.getTransitionId());
        if (entrySnapshots != null) {
            for (ModeTrainingSnapshotRecord snapshot : entrySnapshots) {
                if (snapshot != null && snapshot.getEnvironmentId() != null
                        && frozenTraining.add(snapshot.getEnvironmentId())) {
                    created.add(step(transition, 2, ordinal++, "TRAINING",
                            snapshot.getEnvironmentId(), "RESTORE_TRAINING_ENVIRONMENT"));
                }
            }
        }

        modeMapper.updateTransition(agentId, TRAINING_MODE, EXITING,
                transition.getTransitionId(), now);
        mode.setDesiredMode(TRAINING_MODE);
        mode.setActualMode(EXITING);
        mode.setActiveTransitionId(transition.getTransitionId());
        auditService.recordSuccess("MODE_EXIT_STARTED", identity.userId, agentId,
                transition.getTransitionId());
        if (created.isEmpty()) {
            completeTransition(transition, mode, now);
        } else {
            dispatchAfterCommit(transition, created, agent, identity);
        }
        return view(transition, created);
    }

    @Transactional
    public ModeTransitionView retry(String transitionId, User actor) {
        requireSuperAdmin(actor);
        ModeTransitionRecord transition = transitionMapper.selectForUpdate(transitionId);
        if (transition == null) {
            throw new IllegalArgumentException("模式切换不存在");
        }
        ProcessingAgentModeRecord mode = modeMapper.selectForUpdate(transition.getAgentId());
        if (mode == null || !transitionId.equals(mode.getActiveTransitionId())
                || !transition.getTargetMode().equals(mode.getDesiredMode())) {
            throw new ModeConflictException("AGENT_MODE_TRANSITION_CONFLICT",
                    "Only the current transition target can be retried");
        }
        if (!"DEGRADED".equals(transition.getState())) {
            throw new ModeConflictException("MODE_TRANSITION_NOT_DEGRADED",
                    "Only a degraded transition can be retried");
        }
        List<ModeTransitionStepRecord> all = new ArrayList<>(safeSteps(transitionId));
        ProcessingAgentRecord agent = agentMapper.selectForManagement(transition.getAgentId());
        List<TrainingEnvironmentRecord> environments = safeTraining(transition.getAgentId());
        String preflightFailure = preflightFailure(agent, environments);
        if (preflightFailure != null) {
            LocalDateTime now = utcNow();
            transitionMapper.updateState(transitionId, "DEGRADED", preflightFailure, now);
            transition.setFailureSummary(preflightFailure);
            modeMapper.updateTransition(transition.getAgentId(), transition.getTargetMode(),
                    "DEGRADED", transitionId, now);
            mode.setActualMode("DEGRADED");
            return view(transition, all);
        }
        if (all.isEmpty() && COMPETITION_MODE.equals(transition.getTargetMode())) {
            all.addAll(materializeEntrySteps(transition, environments, utcNow()));
            if (all.isEmpty()) {
                completeTransition(transition, mode, utcNow());
                return view(transition, all);
            }
        }
        int phase = currentIncompletePhase(all);
        if (phase == 0) {
            throw new ModeConflictException("MODE_TRANSITION_NOT_RETRYABLE",
                    "Transition has no incomplete phase");
        }
        LocalDateTime now = utcNow();
        stepMapper.resetFailedInPhase(transitionId, phase, now);
        for (ModeTransitionStepRecord step : all) {
            if (step.getPhaseNumber() != null && step.getPhaseNumber() == phase
                    && "FAILED".equals(step.getState())) {
                step.setState("PENDING");
                step.setCommandId(null);
                step.setResultCode(null);
                step.setResultMessage(null);
                step.setComponentResultsJson(null);
            }
        }
        transitionMapper.updateState(transitionId, "RUNNING", null, now);
        transition.setState("RUNNING");
        transition.setFailureSummary(null);
        String actual = COMPETITION_MODE.equals(transition.getTargetMode()) ? ENTERING : EXITING;
        modeMapper.updateTransition(transition.getAgentId(), transition.getTargetMode(), actual,
                transitionId, now);
        dispatchAfterCommit(transitionId);
        return view(transition, all);
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
    public void dispatchReadyPhase(String transitionId) {
        dispatcher.dispatchReadyPhase(transitionId);
    }

    @Transactional
    public void advanceAfterSuccessfulStep(String transitionId) {
        ModeTransitionRecord transition = transitionMapper.selectForUpdate(transitionId);
        if (transition == null || "SUCCEEDED".equals(transition.getState())) {
            return;
        }
        List<ModeTransitionStepRecord> all = safeSteps(transitionId);
        if (all.isEmpty() && "DEGRADED".equals(transition.getState())) {
            return;
        }
        for (ModeTransitionStepRecord step : all) {
            if ("FAILED".equals(step.getState())) {
                return;
            }
            if (!"SUCCEEDED".equals(step.getState())) {
                dispatchAfterCommit(transitionId);
                return;
            }
        }
        LocalDateTime now = utcNow();
        ProcessingAgentModeRecord mode = modeMapper.selectForUpdate(transition.getAgentId());
        transitionMapper.updateTerminal(transitionId, "SUCCEEDED", null, now);
        transition.setState("SUCCEEDED");
        transition.setCompletedAt(now);
        transition.setFailureSummary(null);
        transition.setActiveTransitionKey(null);
        if (mode != null) {
            String actual = COMPETITION_MODE.equals(transition.getTargetMode())
                    ? COMPETITION_MODE : NORMAL;
            modeMapper.updateTransition(transition.getAgentId(), transition.getTargetMode(),
                    actual, null, now);
            mode.setActualMode(actual);
            mode.setActiveTransitionId(null);
        }
        auditService.recordSuccess(COMPETITION_MODE.equals(transition.getTargetMode())
                        ? "MODE_ENTRY_COMPLETED" : "MODE_EXIT_COMPLETED",
                transition.getActorUserId(), transition.getAgentId(), transitionId);
    }

    @Transactional
    public void convergeAll(String targetMode, int actorUserId, String actorRole) {
        if (!COMPETITION_MODE.equals(targetMode) && !TRAINING_MODE.equals(targetMode)) {
            return;
        }
        List<ProcessingAgentRecord> agents = agentMapper.selectVisibleAgents();
        if (agents == null) {
            return;
        }
        for (ProcessingAgentRecord agent : agents) {
            try {
                if (COMPETITION_MODE.equals(targetMode)) {
                    planEntry(agent.getAgentId(), actorUserId, actorRole);
                } else {
                    User actor = new User();
                    actor.setUserId(actorUserId);
                    actor.setEnabled(true);
                    actor.setRole(actorRole);
                    planExit(agent.getAgentId(), actor);
                }
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
                    dispatcher.dispatchReadyPhase(transition.getTransitionId());
                }
            });
            return;
        }
        dispatcher.dispatchPlanned(transition, created, agent, actor.userId, actor.role);
    }

    private void dispatchAfterCommit(final String transitionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.dispatchReadyPhase(transitionId);
                }
            });
            return;
        }
        dispatcher.dispatchReadyPhase(transitionId);
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

    private ModeTransitionRecord transition(String agentId, String sourceMode, String targetMode,
                                            ActorIdentity actor, LocalDateTime now) {
        ModeTransitionRecord result = new ModeTransitionRecord();
        result.setTransitionId(UUID.randomUUID().toString());
        result.setAgentId(agentId);
        result.setSourceMode(sourceMode);
        result.setTargetMode(targetMode);
        result.setState("RUNNING");
        result.setActiveTransitionKey(agentId + ":" + targetMode);
        result.setActorUserId(actor.userId);
        result.setActorRole(actor.role);
        result.setRequestedAt(now);
        result.setUpdatedAt(now);
        return result;
    }

    private void completeTransition(ModeTransitionRecord transition, ProcessingAgentModeRecord mode,
                                    LocalDateTime now) {
        transition.setState("SUCCEEDED");
        transition.setCompletedAt(now);
        transition.setActiveTransitionKey(null);
        transitionMapper.updateTerminal(transition.getTransitionId(), "SUCCEEDED", null, now);
        String actual = COMPETITION_MODE.equals(transition.getTargetMode()) ? COMPETITION_MODE : NORMAL;
        modeMapper.updateTransition(transition.getAgentId(), transition.getTargetMode(), actual, null, now);
        mode.setActualMode(actual);
        mode.setActiveTransitionId(null);
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
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null
                || agent.getLastSeenAt() == null
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

    private List<ModeTransitionStepRecord> materializeEntrySteps(ModeTransitionRecord transition,
                                                                  List<TrainingEnvironmentRecord> environments,
                                                                  LocalDateTime now) {
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
        Set<String> boundSlots = boundSlotIds(transition.getAgentId());
        ordinal = 1;
        for (CompetitionEnvironmentRecord environment : safeCompetition(transition.getAgentId())) {
            if (boundSlots.contains(environment.getSlotId()) && ready(environment)) {
                created.add(step(transition, 2, ordinal++, "COMPETITION", environment.getEnvironmentId(),
                        "START_COMPETITION_ENVIRONMENT"));
            }
        }
        return created;
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

    private int currentIncompletePhase(List<ModeTransitionStepRecord> steps) {
        int phase = Integer.MAX_VALUE;
        for (ModeTransitionStepRecord step : steps) {
            if (!"SUCCEEDED".equals(step.getState()) && step.getPhaseNumber() != null) {
                phase = Math.min(phase, step.getPhaseNumber());
            }
        }
        return phase == Integer.MAX_VALUE ? 0 : phase;
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

    private void requireSuperAdmin(User actor) {
        if (actor == null || !Boolean.TRUE.equals(actor.getEnabled()) || actor.getUserId() == null
                || !"SUPER_ADMIN".equals(actor.getRole())) {
            throw new IllegalArgumentException("仅超级管理员可以重试模式切换");
        }
    }

    private void requireAgentId(String agentId) {
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new IllegalArgumentException("处理服务器编号不能为空");
        }
    }

    private boolean nonBlank(String value) {
        return value != null && !value.trim().isEmpty();
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
