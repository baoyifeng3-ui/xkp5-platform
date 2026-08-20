package com.match.environment.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.environment.model.TrainingEnvironmentOperationView;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentOperationRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.licensing.guard.LicenseGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class EnvironmentOperationService {
    private final TrainingEnvironmentMapper environmentMapper;
    private final EnvironmentOperationMapper operationMapper;
    private final ProcessingAgentMapper agentMapper;
    private final AgentCommandService commandService;
    private final EnvironmentCommandFactory commandFactory;
    private final LicenseGuard licenseGuard;
    private final Clock clock;

    public EnvironmentOperationService(TrainingEnvironmentMapper environmentMapper,
                                       EnvironmentOperationMapper operationMapper,
                                       ProcessingAgentMapper agentMapper,
                                       AgentCommandService commandService,
                                       EnvironmentCommandFactory commandFactory,
                                       LicenseGuard licenseGuard,
                                       Clock clock) {
        this.environmentMapper = environmentMapper;
        this.operationMapper = operationMapper;
        this.agentMapper = agentMapper;
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.licenseGuard = licenseGuard;
        this.clock = clock;
    }

    @Transactional
    public TrainingEnvironmentOperationView start(String environmentId, int actorUserId, String actorRole) {
        licenseGuard.requireActive();
        List<TrainingEnvironmentRecord> environments = lockUserEnvironments(environmentId);
        TrainingEnvironmentRecord target = requireTarget(environments, environmentId);
        requireAccess(target, actorUserId, actorRole, "START");
        TrainingEnvironmentOperationView active = activeOperation(target, "START");
        if (active != null) {
            return active;
        }
        if ("RUNNING".equals(target.getDesiredState()) && "RUNNING".equals(target.getActualState())) {
            return noOperation(target, "SUCCEEDED");
        }

        boolean waiting = false;
        for (TrainingEnvironmentRecord environment : environments) {
            if (environmentId.equals(environment.getEnvironmentId()) || isStopped(environment)) {
                continue;
            }
            if (operationMapper.selectActive(environment.getEnvironmentId()) != null) {
                throw new IllegalArgumentException("其他课程环境正在执行操作");
            }
            createDispatchedOperation(environment, "STOP", "STOPPED", "STOPPING",
                    actorUserId, actorRole);
            waiting = true;
        }
        if (waiting) {
            return createWaitingStart(target, actorUserId, actorRole);
        }
        return createDispatchedOperation(target, "START", "RUNNING", "STARTING",
                actorUserId, actorRole);
    }

    @Transactional
    public TrainingEnvironmentOperationView stop(String environmentId, int actorUserId, String actorRole) {
        List<TrainingEnvironmentRecord> environments = lockUserEnvironments(environmentId);
        TrainingEnvironmentRecord target = requireTarget(environments, environmentId);
        requireAccess(target, actorUserId, actorRole, "STOP");
        TrainingEnvironmentOperationView active = activeOperation(target, "STOP");
        if (active != null) {
            return active;
        }
        if (isStopped(target)) {
            return noOperation(target, "SUCCEEDED");
        }
        return createDispatchedOperation(target, "STOP", "STOPPED", "STOPPING",
                actorUserId, actorRole);
    }

    @Transactional
    public TrainingEnvironmentOperationView restore(String environmentId, int actorUserId, String actorRole) {
        licenseGuard.requireActive();
        List<TrainingEnvironmentRecord> environments = lockUserEnvironments(environmentId);
        TrainingEnvironmentRecord target = requireTarget(environments, environmentId);
        requireAccess(target, actorUserId, actorRole, "RESTORE");
        TrainingEnvironmentOperationView active = activeOperation(target, "RESTORE");
        if (active != null) {
            return active;
        }
        return createDispatchedOperation(target, "RESTORE", "STOPPED", "RESTORING",
                actorUserId, actorRole);
    }

    @Transactional(readOnly = true)
    public List<TrainingEnvironmentRecord> listForUser(int userId) {
        return environmentMapper.selectByUser(userId);
    }

    @Transactional(readOnly = true)
    public List<TrainingEnvironmentRecord> listAll() {
        return environmentMapper.selectAllEnvironments();
    }

    private TrainingEnvironmentOperationView createWaitingStart(TrainingEnvironmentRecord environment,
                                                                 int actorUserId, String actorRole) {
        String operationId = UUID.randomUUID().toString();
        updateState(environment, "RUNNING", "WAITING_DEPENDENCY", operationId, actorUserId);
        EnvironmentOperationRecord operation = operation(environment, operationId, "START",
                actorUserId, actorRole, "WAITING_DEPENDENCY");
        operationMapper.insert(operation);
        return view(operation, null);
    }

    private TrainingEnvironmentOperationView createDispatchedOperation(TrainingEnvironmentRecord environment,
                                                                        String operationType,
                                                                        String desiredState,
                                                                        String actualState,
                                                                        int actorUserId,
                                                                        String actorRole) {
        String operationId = UUID.randomUUID().toString();
        updateState(environment, desiredState, actualState, operationId, actorUserId);
        EnvironmentOperationRecord operation = operation(environment, operationId, operationType,
                actorUserId, actorRole, "PENDING");
        operationMapper.insert(operation);
        ProcessingAgentRecord agent = requireAgent(environment.getAgentId());
        String commandType = operationType + "_TRAINING_ENVIRONMENT";
        AgentCommandView command = commandService.requestEnvironmentCommand(agent, commandType,
                commandFactory.createPayloadJson(environment, operationId), actorUserId, actorRole,
                environment.getEnvironmentId() + ":" + operationType);
        operation.setCommandId(command.getCommandId());
        operationMapper.updateById(operation);
        return view(operation, command);
    }

    private void updateState(TrainingEnvironmentRecord environment, String desiredState,
                             String actualState, String operationId, int actorUserId) {
        if (environmentMapper.compareAndSetState(environment.getEnvironmentId(), environment.getLockVersion(),
                desiredState, actualState, operationId, actorUserId, now()) != 1) {
            throw new IllegalArgumentException("环境状态已变化，请刷新后重试");
        }
        environment.setDesiredState(desiredState);
        environment.setActualState(actualState);
        environment.setCurrentOperationId(operationId);
        environment.setLockVersion(environment.getLockVersion() + 1);
    }

    private EnvironmentOperationRecord operation(TrainingEnvironmentRecord environment, String operationId,
                                                 String operationType, int actorUserId, String actorRole,
                                                 String state) {
        EnvironmentOperationRecord operation = new EnvironmentOperationRecord();
        operation.setOperationId(operationId);
        operation.setEnvironmentId(environment.getEnvironmentId());
        operation.setOperationType(operationType);
        operation.setActorUserId(actorUserId);
        operation.setActorRole(actorRole);
        operation.setState(state);
        operation.setActiveOperationKey(environment.getEnvironmentId() + ":" + operationType);
        operation.setCorrelationId(UUID.randomUUID().toString());
        operation.setRequestedAt(now());
        operation.setUpdatedAt(now());
        return operation;
    }

    private TrainingEnvironmentOperationView activeOperation(TrainingEnvironmentRecord environment,
                                                             String requestedType) {
        EnvironmentOperationRecord active = operationMapper.selectActive(environment.getEnvironmentId());
        if (active == null) {
            return null;
        }
        if (!requestedType.equals(active.getOperationType())) {
            throw new IllegalArgumentException("环境正在执行其他操作");
        }
        return view(active, null);
    }

    private List<TrainingEnvironmentRecord> lockUserEnvironments(String environmentId) {
        if (environmentId == null || environmentId.trim().isEmpty()) {
            throw new IllegalArgumentException("环境编号不能为空");
        }
        Integer userId = environmentMapper.selectUserId(environmentId);
        if (userId == null) {
            throw new IllegalArgumentException("实训环境不存在");
        }
        return environmentMapper.selectUserEnvironmentsForUpdate(userId);
    }

    private TrainingEnvironmentRecord requireTarget(List<TrainingEnvironmentRecord> environments,
                                                    String environmentId) {
        if (environments != null) {
            for (TrainingEnvironmentRecord environment : environments) {
                if (environmentId.equals(environment.getEnvironmentId())) {
                    return environment;
                }
            }
        }
        throw new IllegalArgumentException("实训环境不存在");
    }

    private void requireAccess(TrainingEnvironmentRecord environment, int actorUserId,
                               String actorRole, String operationType) {
        if ("SYSTEM".equals(actorRole)) {
            if (!"STOP".equals(operationType)) {
                throw new IllegalArgumentException("系统角色只能停止实训环境");
            }
            return;
        }
        if (!"SUPER_ADMIN".equals(actorRole) && !"ADMIN".equals(actorRole) && !"USER".equals(actorRole)) {
            throw new IllegalArgumentException("环境操作角色无效");
        }
        if ("USER".equals(actorRole)) {
            if (environment.getUserId() == null || environment.getUserId() != actorUserId) {
                throw new IllegalArgumentException("普通用户只能操作自己的实训环境");
            }
            if (!"START".equals(operationType)) {
                throw new IllegalArgumentException("普通用户只能执行一键上课");
            }
        }
    }

    private ProcessingAgentRecord requireAgent(String agentId) {
        ProcessingAgentRecord agent = agentMapper.selectForManagement(agentId);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null) {
            throw new IllegalArgumentException("处理服务器不可用");
        }
        return agent;
    }

    private boolean isStopped(TrainingEnvironmentRecord environment) {
        return "STOPPED".equals(environment.getDesiredState())
                && "STOPPED".equals(environment.getActualState());
    }

    private TrainingEnvironmentOperationView noOperation(TrainingEnvironmentRecord environment, String state) {
        TrainingEnvironmentOperationView view = new TrainingEnvironmentOperationView();
        view.setEnvironmentId(environment.getEnvironmentId());
        view.setState(state);
        return view;
    }

    private TrainingEnvironmentOperationView view(EnvironmentOperationRecord operation, AgentCommandView command) {
        TrainingEnvironmentOperationView view = new TrainingEnvironmentOperationView();
        view.setEnvironmentId(operation.getEnvironmentId());
        view.setOperationId(operation.getOperationId());
        view.setState(operation.getState());
        view.setCommand(command);
        return view;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
