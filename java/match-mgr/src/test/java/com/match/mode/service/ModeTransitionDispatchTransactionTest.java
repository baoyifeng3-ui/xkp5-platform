package com.match.mode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentAuditService;
import com.match.agent.service.AgentCommandService;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.service.EnvironmentCommandFactory;
import com.match.mode.persistence.ModeTransitionMapper;
import com.match.mode.persistence.ModeTransitionRecord;
import com.match.mode.persistence.ModeTransitionStepMapper;
import com.match.mode.persistence.ModeTransitionStepRecord;
import com.match.mode.persistence.ProcessingAgentModeMapper;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModeTransitionDispatchTransactionTest {
    private static final Instant NOW = Instant.parse("2026-08-21T06:07:08Z");

    @Test
    public void commandRollbackDoesNotRollbackIndependentFailureDegradation() {
        RecordingTransactionManager transactions = new RecordingTransactionManager();
        ProcessingAgentCommandMapper commandMapper = mock(ProcessingAgentCommandMapper.class);
        when(commandMapper.selectEnabledAgentForUpdate("agent-1")).thenReturn("agent-1");
        when(commandMapper.insert(any())).thenThrow(new IllegalStateException("command insert failed"));
        AgentCommandService commandTarget = new AgentCommandService(commandMapper,
                new ObjectMapper(), mock(AgentAuditService.class), fixedClock());
        AgentCommandService commandService = proxy(commandTarget, transactions);

        ModeTransitionMapper transitionMapper = mock(ModeTransitionMapper.class);
        ModeTransitionStepMapper stepMapper = mock(ModeTransitionStepMapper.class);
        ProcessingAgentModeMapper modeMapper = mock(ProcessingAgentModeMapper.class);
        ProcessingAgentMapper agentMapper = mock(ProcessingAgentMapper.class);
        TrainingEnvironmentMapper trainingMapper = mock(TrainingEnvironmentMapper.class);
        CompetitionEnvironmentMapper competitionMapper = mock(CompetitionEnvironmentMapper.class);
        EnvironmentCommandFactory commandFactory = mock(EnvironmentCommandFactory.class);
        ModeTransitionRecord transition = transition();
        ModeTransitionStepRecord step = step();
        ProcessingAgentRecord agent = agent();
        CompetitionEnvironmentRecord environment = new CompetitionEnvironmentRecord();
        environment.setEnvironmentId(step.getEnvironmentId());
        when(transitionMapper.selectForUpdate("transition-1")).thenReturn(transition);
        when(stepMapper.selectByTransition("transition-1"))
                .thenReturn(Collections.singletonList(step));
        when(stepMapper.selectForUpdate("step-1")).thenReturn(step);
        when(agentMapper.selectForManagement("agent-1")).thenReturn(agent);
        when(competitionMapper.selectForUpdate("environment-1")).thenReturn(environment);
        when(commandFactory.createPayloadJson(environment, "step-1")).thenReturn("{}");
        when(stepMapper.markTerminal(eq("step-1"), eq("FAILED"),
                eq("COMMAND_DISPATCH_FAILED"), anyString(), eq(null),
                any(LocalDateTime.class))).thenReturn(1);
        ModeTransitionDispatchWorker workerTarget = new ModeTransitionDispatchWorker(
                transitionMapper, stepMapper, modeMapper, agentMapper, trainingMapper,
                competitionMapper, commandService, commandFactory, fixedClock());
        ModeTransitionDispatchWorker worker = proxy(workerTarget, transactions);
        ModeTransitionDispatchCoordinator coordinator = new ModeTransitionDispatchCoordinator(worker);

        coordinator.dispatchReadyPhase("transition-1");

        assertEquals(1, transactions.commits);
        assertEquals(1, transactions.rollbacks);
        verify(stepMapper).markTerminal(eq("step-1"), eq("FAILED"),
                eq("COMMAND_DISPATCH_FAILED"), eq("command insert failed"), eq(null),
                any(LocalDateTime.class));
        verify(transitionMapper).updateState(eq("transition-1"), eq("DEGRADED"),
                eq("COMMAND_DISPATCH_FAILED"), any(LocalDateTime.class));
        verify(modeMapper).updateTransition(eq("agent-1"), eq("COMPETITION"), eq("DEGRADED"),
                eq("transition-1"), any(LocalDateTime.class));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, RecordingTransactionManager transactionManager) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactionManager,
                new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private ModeTransitionRecord transition() {
        ModeTransitionRecord result = new ModeTransitionRecord();
        result.setTransitionId("transition-1");
        result.setAgentId("agent-1");
        result.setTargetMode("COMPETITION");
        result.setState("RUNNING");
        result.setActorUserId(9);
        result.setActorRole("ADMIN");
        return result;
    }

    private ModeTransitionStepRecord step() {
        ModeTransitionStepRecord result = new ModeTransitionStepRecord();
        result.setStepId("step-1");
        result.setTransitionId("transition-1");
        result.setPhaseNumber(1);
        result.setStepOrdinal(1);
        result.setEnvironmentKind("COMPETITION");
        result.setEnvironmentId("environment-1");
        result.setActionType("STOP_COMPETITION_ENVIRONMENT");
        result.setState("PENDING");
        result.setIdempotencyKey("mode:transition-1:1:environment-1:STOP_COMPETITION_ENVIRONMENT");
        return result;
    }

    private ProcessingAgentRecord agent() {
        ProcessingAgentRecord result = new ProcessingAgentRecord();
        result.setAgentId("agent-1");
        result.setEnabled(true);
        result.setLastSeenAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return result;
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final ThreadLocal<TransactionObject> current = new ThreadLocal<>();
        private int commits;
        private int rollbacks;

        @Override
        protected Object doGetTransaction() throws TransactionException {
            TransactionObject existing = current.get();
            return existing == null ? new TransactionObject() : existing;
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) throws TransactionException {
            return ((TransactionObject) transaction).active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition)
                throws TransactionException {
            TransactionObject started = (TransactionObject) transaction;
            started.active = true;
            current.set(started);
        }

        @Override
        protected Object doSuspend(Object transaction) throws TransactionException {
            current.remove();
            return transaction;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources)
                throws TransactionException {
            current.set((TransactionObject) suspendedResources);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
            rollbacks++;
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
            ((TransactionObject) status.getTransaction()).rollbackOnly = true;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            TransactionObject completed = (TransactionObject) transaction;
            completed.active = false;
            completed.rollbackOnly = false;
            if (current.get() == completed) {
                current.remove();
            }
        }
    }

    private static final class TransactionObject implements SmartTransactionObject {
        private boolean active;
        private boolean rollbackOnly;

        @Override
        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        @Override
        public void flush() {
        }
    }
}
