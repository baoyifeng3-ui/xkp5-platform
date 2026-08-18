package com.match.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AgentCommandFixtureContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    public void sharedShutdownFixtureMatchesVersionOneContract() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/agent-command-shutdown-v1.json")) {
            assertNotNull(input);
            AgentCommandEnvelope command = mapper.readValue(input, AgentCommandEnvelope.class);
            assertEquals("11111111-2222-4333-8444-555555555555", command.getCommandId());
            assertEquals("SHUTDOWN_SERVER", command.getType());
            assertEquals(Integer.valueOf(1), command.getVersion());
            assertEquals("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", command.getLeaseToken());
            assertEquals("2026-08-19T12:05:00Z", command.getLeaseExpiresAt().toString());
            assertNotNull(command.getPayload());
            assertTrue(command.getPayload().isObject());
            assertEquals(0, command.getPayload().size());
        }
    }

    @Test(expected = com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class)
    public void strictDeserializerRejectsUnknownCommandField() throws Exception {
        mapper.readValue("{\"commandId\":\"11111111-2222-4333-8444-555555555555\","
                        + "\"type\":\"SHUTDOWN_SERVER\",\"version\":1,"
                        + "\"leaseToken\":\"aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee\","
                        + "\"leaseExpiresAt\":\"2026-08-19T12:05:00Z\",\"payload\":{},"
                        + "\"shell\":\"poweroff\"}", AgentCommandEnvelope.class);
    }
}
