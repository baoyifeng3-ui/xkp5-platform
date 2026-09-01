package com.match.agent.service;

import com.match.agent.model.AgentCommandEnvelope;
import com.match.agent.model.AgentCommandPollResponse;
import com.match.agent.persistence.ProcessingAgentRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
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
                List<AgentCommandEnvelope> batch = new ArrayList<>();
                batch.add(command.get());
                if (isControl(command.get())) {
                    while (batch.size() < 4) {
                        Optional<AgentCommandEnvelope> next = commandService.lease(agent);
                        if (!next.isPresent()) break;
                        batch.add(next.get());
                        if (!isControl(next.get())) break;
                    }
                }
                return new AgentCommandPollResponse(batch);
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

    private boolean isControl(AgentCommandEnvelope command) {
        return command != null && ("START_TRAINING_ENVIRONMENT".equals(command.getType())
                || "STOP_TRAINING_ENVIRONMENT".equals(command.getType())
                || "START_COMPETITION_ENVIRONMENT".equals(command.getType())
                || "STOP_COMPETITION_ENVIRONMENT".equals(command.getType()));
    }

    private long saturatedAdd(long left, long right) {
        long result = left + right;
        return result < left ? Long.MAX_VALUE : result;
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
