package com.match.agent.service;

import com.match.agent.model.AgentCommandEnvelope;
import com.match.agent.model.AgentCommandPollResponse;
import com.match.agent.persistence.ProcessingAgentRecord;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentCommandPollerTest {
    @Test
    public void productionConstructorIsTheExplicitSpringInjectionPoint() throws Exception {
        Constructor<AgentCommandPoller> constructor = AgentCommandPoller.class.getConstructor(
                AgentCommandService.class);

        org.junit.Assert.assertTrue("multiple constructors require an explicit Spring injection point",
                constructor.isAnnotationPresent(Autowired.class));
    }

    @Test
    public void zeroWaitStillAttemptsOneLease() {
        AgentCommandService commands = mock(AgentCommandService.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        when(commands.lease(agent)).thenReturn(Optional.empty());
        AgentCommandPoller poller = new AgentCommandPoller(commands, () -> 10L,
                millis -> { throw new AssertionError("must not sleep"); });

        AgentCommandPollResponse response = poller.poll(agent, 0);

        assertEquals(0, response.getCommands().size());
        verify(commands).lease(agent);
    }

    @Test
    public void availableCommandReturnsWithoutSleeping() throws Exception {
        AgentCommandService commands = mock(AgentCommandService.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        AgentCommandEnvelope command = new AgentCommandEnvelope();
        command.setType("START_TRAINING_ENVIRONMENT");
        when(commands.lease(agent)).thenReturn(Optional.of(command));
        AgentCommandPoller.Sleeper sleeper = mock(AgentCommandPoller.Sleeper.class);
        AgentCommandPoller poller = new AgentCommandPoller(commands, () -> 10L, sleeper);

        AgentCommandPollResponse response = poller.poll(agent, 25);

        assertEquals(1, response.getCommands().size());
        assertSame(command, response.getCommands().get(0));
        verify(sleeper, never()).sleep(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    public void emptyPollStopsAtDeadlineWithBoundedSleeps() {
        AgentCommandService commands = mock(AgentCommandService.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        when(commands.lease(agent)).thenReturn(Optional.empty());
        AtomicLong nanos = new AtomicLong();
        AtomicInteger sleeps = new AtomicInteger();
        AgentCommandPoller poller = new AgentCommandPoller(commands, nanos::get, millis -> {
            if (millis < 1 || millis > 250) {
                throw new AssertionError("sleep out of range: " + millis);
            }
            sleeps.incrementAndGet();
            nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
        });

        AgentCommandPollResponse response = poller.poll(agent, 1);

        assertEquals(0, response.getCommands().size());
        assertEquals(TimeUnit.SECONDS.toNanos(1), nanos.get());
        assertEquals(4, sleeps.get());
    }
}
