package com.match.agent.persistence;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AgentMetricAggregationSchemaTest {
    @Test
    public void nullableMetricsHaveIndependentSampleCounts() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/db/migration/V19__processing_agent_metric_sample_counts.sql");
        assertNotNull("missing V19 metric sample-count migration", input);
        try (InputStream closeable = input) {
            byte[] bytes = new byte[closeable.available()];
            int read = closeable.read(bytes);
            String sql = new String(bytes, 0, read, StandardCharsets.UTF_8);
            assertTrue(sql.contains("cpu_sample_count"));
            assertTrue(sql.contains("gpu_sample_count"));
            assertTrue(sql.contains("workspace_disk_sample_count"));
        }
    }
}
