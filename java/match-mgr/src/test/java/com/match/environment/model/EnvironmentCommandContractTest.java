package com.match.environment.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandEnvelope;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EnvironmentCommandContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    public void sharedFixturesUseTypedVersionOneEnvironmentContract() throws Exception {
        assertFixture("create", "CREATE_TRAINING_ENVIRONMENT", true);
        assertFixture("restore", "RESTORE_TRAINING_ENVIRONMENT", true);
        assertFixture("start", "START_TRAINING_ENVIRONMENT", false);
        assertFixture("stop", "STOP_TRAINING_ENVIRONMENT", false);
    }

    private void assertFixture(String operation, String type, boolean fullSpec) throws Exception {
        String path = "/fixtures/agent-command-" + operation + "-training-environment-v1.json";
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(path, input);
            AgentCommandEnvelope envelope = mapper.readValue(input, AgentCommandEnvelope.class);
            EnvironmentCommandPayload payload = mapper.treeToValue(envelope.getPayload(),
                    EnvironmentCommandPayload.class);
            assertEquals(type, envelope.getType());
            assertEquals(Integer.valueOf(1), envelope.getVersion());
            assertEquals("77777777-2222-4333-8444-555555555551", payload.getEnvironmentId());
            assertEquals(2, payload.getComponents().size());
            assertEquals("ANNOTATION", payload.getComponents().get(0).getComponentType());
            assertEquals("EDITOR", payload.getComponents().get(1).getComponentType());
            if (fullSpec) {
                assertEquals("training/7/101", payload.getWorkspaceRelativePath());
                assertEquals("sysbox-runc", payload.getComponents().get(0).getRuntimeName());
                assertEquals("nvidia", payload.getComponents().get(1).getRuntimeName());
                assertEquals("/root/data", payload.getComponents().get(0).getMountTarget());
                assertEquals("/home/student/data", payload.getComponents().get(1).getMountTarget());
            }
        }
    }
}
