package com.match.agent.service;

import com.match.agent.persistence.AgentMetricMinuteMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class AgentMetricRetention {
    private final AgentMetricMinuteMapper mapper;
    private final Clock clock;

    public AgentMetricRetention(AgentMetricMinuteMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Scheduled(cron = "${xkp.agent.metric-retention-cron:0 20 2 * * *}")
    public void cleanup() {
        mapper.deleteBefore(LocalDateTime.ofInstant(clock.instant().minusSeconds(30L * 24 * 60 * 60),
                ZoneOffset.UTC));
    }
}
