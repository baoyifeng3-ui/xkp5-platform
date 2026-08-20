package com.match.agent.service;

import com.match.agent.model.AgentCommandEnvelope;
import com.match.agent.model.AgentCommandPollResponse;
import com.match.agent.persistence.ProcessingAgentRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Service
public class AgentCommandPoller {
    private static final long RETRY_MILLIS = 250;

    private final AgentCommandService commandService;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;

    @Autowired
    public AgentCommandPoller(AgentCommandService commandService) {
        this(commandService, System::nanoTime, Thread::sleep);
    }

    AgentCommandPoller(AgentCommandService commandService, LongSupplier nanoTime, Sleeper sleeper) {
        this.commandService = commandService;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    public AgentCommandPollResponse poll(ProcessingAgentRecord agent, int waitSeconds) {
        long waitNanos = TimeUnit.SECONDS.toNanos(waitSeconds);
        long startedAt = nanoTime.getAsLong();
        long deadline = saturatedAdd(startedAt, waitNanos);
        while (true) {
            Optional<AgentCommandEnvelope> command = commandService.lease(agent);
            if (command.isPresent()) {
                return new AgentCommandPollResponse(Collections.singletonList(command.get()));
            }
            long remaining = deadline - nanoTime.getAsLong();
            if (remaining <= 0) {
                return new AgentCommandPollResponse();
            }
            long sleepMillis = Math.min(RETRY_MILLIS,
                    Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            try {
                sleeper.sleep(sleepMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new AgentCommandPollResponse();
            }
        }
    }

    private long saturatedAdd(long left, long right) {
        long result = left + right;
        return result < left ? Long.MAX_VALUE : result;
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
