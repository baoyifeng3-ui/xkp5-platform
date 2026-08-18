package com.match.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;

public class AgentFixtureContractTest {
    @Test
    public void strictDeserializerAcceptsSharedHeartbeatV1() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        try (InputStream input = getClass().getResourceAsStream("/agent/heartbeat-v1.json")) {
            AgentHeartbeatRequest request = mapper.readValue(input, AgentHeartbeatRequest.class);
            assertEquals(Long.valueOf(17), request.getSequence());
            assertEquals("NVIDIA GeForce RTX 2080", request.getMetrics().getGpuModel());
        }
    }

    @Test(expected = com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class)
    public void strictDeserializerRejectsUnknownHeartbeatField() throws Exception {
        new ObjectMapper().findAndRegisterModules().readValue(
                "{\"agentId\":\"a\",\"bootId\":\"22222222-2222-4222-8222-222222222222\","
                        + "\"sequence\":1,\"timestamp\":\"2026-08-18T13:15:30Z\","
                        + "\"agentVersion\":\"0.1.0\",\"metrics\":{},\"unexpected\":true}",
                AgentHeartbeatRequest.class);
    }
}
