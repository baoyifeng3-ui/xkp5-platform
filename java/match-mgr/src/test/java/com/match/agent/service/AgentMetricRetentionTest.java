package com.match.agent.service;

import com.match.agent.persistence.AgentMetricMinuteMapper;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AgentMetricRetentionTest {
    @Test
    public void removesOnlyBucketsStrictlyOlderThanThirtyDays() {
        AgentMetricMinuteMapper mapper = mock(AgentMetricMinuteMapper.class);
        Instant now = Instant.parse("2026-08-18T13:00:00Z");
        AgentMetricRetention retention = new AgentMetricRetention(mapper, Clock.fixed(now, ZoneOffset.UTC));

        retention.cleanup();

        verify(mapper).deleteBefore(LocalDateTime.ofInstant(now.minusSeconds(30L * 24 * 60 * 60),
                ZoneOffset.UTC));
    }
}
