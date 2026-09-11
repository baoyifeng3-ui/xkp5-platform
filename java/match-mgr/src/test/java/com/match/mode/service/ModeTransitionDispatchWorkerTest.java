package com.match.mode.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModeTransitionDispatchWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-21T06:07:08Z");

    @Test
    public void commandAndStepAssociationRunInTransactionAndFailClosedOnCasConflict()
            throws Exception {
        ModeTransitionMapper transitions = mock(ModeTransitionMapper.class);
        ModeTransitionStepMapper steps = mock(ModeTransitionStepMapper.class);
        ProcessingAgentModeMapper modes = mock(ProcessingAgentModeMapper.class);
        ProcessingAgentMapper agents = mock(ProcessingAgentMapper.class);
        TrainingEnvironmentMapper training = mock(TrainingEnvironmentMapper.class);
        CompetitionEnvironmentMapper competition = mock(CompetitionEnvironmentMapper.class);
        AgentCommandService commands = mock(AgentCommandService.class);
        EnvironmentCommandFactory factory = mock(EnvironmentCommandFactory.class);
        ModeTransitionDispatchWorker worker = new ModeTransitionDispatchWorker(transitions, steps,
                modes, agents, training, competition, commands, factory,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ModeTransitionRecord transition = transition();
        ModeTransitionStepRecord step = pendingStep();
        ProcessingAgentRecord agent = agent();
        CompetitionEnvironmentRecord environment = new CompetitionEnvironmentRecord();
        environment.setEnvironmentId(step.getEnvironmentId());
        AgentCommandView command = new AgentCommandView();
        command.setCommandId("command-1");
        when(transitions.selectForUpdate("transition-1")).thenReturn(transition);
        when(steps.selectByTransition("transition-1")).thenReturn(Collections.singletonList(step));
        when(agents.selectForManagement("agent-1")).thenReturn(agent);
        when(competition.selectForUpdate(step.getEnvironmentId())).thenReturn(environment);
        when(factory.createPayloadJson(environment, step.getStepId())).thenReturn("{}");
        when(commands.requestEnvironmentCommand(eq(agent), eq(step.getActionType()), eq("{}"),
                eq(9), eq("ADMIN"), eq(step.getIdempotencyKey()))).thenReturn(command);
        when(steps.markDispatched(eq(step.getStepId()), eq("command-1"),
                any(LocalDateTime.class))).thenReturn(0);

        Transactional transaction = ModeTransitionDispatchWorker.class
                .getMethod("dispatchReadyPhase", String.class)
                .getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertEquals(Propagation.REQUIRES_NEW, transaction.propagation());
        try {
            worker.dispatchReadyPhase("transition-1");
            fail("expected association conflict");
        } catch (IllegalStateException expected) {
            // The exception escapes the transactional boundary so the command insert rolls back.
        }

        verify(steps, never()).markTerminal(anyString(), anyString(), any(), any(),
                any(), any(LocalDateTime.class));
        verify(modes, never()).updateTransition(anyString(), anyString(), anyString(),
                anyString(), any(LocalDateTime.class));
        verify(factory).createControlPayloadJson(environment, step.getStepId());
        verify(factory, never()).createPayloadJson(environment, step.getStepId());
    }

    @Test
    public void offlineRecoveryDegradesTransitionAndAgentModeTogether() {
        ModeTransitionMapper transitions = mock(ModeTransitionMapper.class);
        ModeTransitionStepMapper steps = mock(ModeTransitionStepMapper.class);
        ProcessingAgentModeMapper modes = mock(ProcessingAgentModeMapper.class);
        ProcessingAgentMapper agents = mock(ProcessingAgentMapper.class);
        ModeTransitionDispatchWorker worker = new ModeTransitionDispatchWorker(transitions, steps,
                modes, agents, mock(TrainingEnvironmentMapper.class),
                mock(CompetitionEnvironmentMapper.class), mock(AgentCommandService.class),
                mock(EnvironmentCommandFactory.class), Clock.fixed(NOW, ZoneOffset.UTC));
        ModeTransitionRecord transition = transition();
        ProcessingAgentRecord offline = agent();
        offline.setLastSeenAt(LocalDateTime.ofInstant(NOW.minusSeconds(301), ZoneOffset.UTC));
        when(transitions.selectForUpdate("transition-1")).thenReturn(transition);
        when(agents.selectForManagement("agent-1")).thenReturn(offline);

        worker.dispatchReadyPhase("transition-1");

        verify(transitions).updateState(eq("transition-1"), eq("DEGRADED"),
                eq("AGENT_OFFLINE"), any(LocalDateTime.class));
        verify(modes).updateTransition(eq("agent-1"), eq("COMPETITION"), eq("DEGRADED"),
                eq("transition-1"), any(LocalDateTime.class));
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

    private ModeTransitionStepRecord pendingStep() {
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
}
