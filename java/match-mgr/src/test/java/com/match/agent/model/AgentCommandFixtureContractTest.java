package com.match.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentAuditService;
import com.match.agent.service.AgentCommandService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentCommandFixtureContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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

    @Test
    public void sharedRootTerminalFixtureMatchesServiceVersionOneContract() throws Exception {
        JsonNode fixture;
        try (InputStream input = getClass().getResourceAsStream(
                "/agent/command-open-root-terminal-v1.json")) {
            assertNotNull(input);
            fixture = mapper.readTree(input);
        }

        AgentCommandEnvelope command = mapper.treeToValue(fixture, AgentCommandEnvelope.class);
        assertEquals("77777777-7777-4777-8777-777777777777", command.getCommandId());
        assertEquals("OPEN_ROOT_TERMINAL", command.getType());
        assertEquals(Integer.valueOf(1), command.getVersion());
        assertEquals("66666666-6666-4666-8666-666666666666", command.getLeaseToken());
        assertEquals("2026-08-19T12:05:00Z", command.getLeaseExpiresAt().toString());

        JsonNode payload = command.getPayload();
        assertNotNull(payload);
        assertTrue(payload.isObject());
        assertEquals(5, payload.size());
        assertEquals(new HashSet<>(Arrays.asList("sessionId", "relayUrl",
                "agentConnectionDeadline", "idleTimeoutSeconds", "absoluteExpiresAt")),
                fieldNames(payload));
        assertTrue(payload.get("sessionId").isTextual());
        assertEquals("44444444-4444-4444-8444-444444444444",
                payload.get("sessionId").textValue());
        assertTrue(payload.get("relayUrl").isTextual());
        assertEquals("wss://management.example/terminal/v1/agent/"
                        + "44444444-4444-4444-8444-444444444444",
                payload.get("relayUrl").textValue());
        assertTrue(payload.get("agentConnectionDeadline").isTextual());
        assertEquals("2026-08-19T12:01:30Z",
                payload.get("agentConnectionDeadline").textValue());
        assertTrue(payload.get("idleTimeoutSeconds").isIntegralNumber());
        assertEquals(600, payload.get("idleTimeoutSeconds").intValue());
        assertTrue(payload.get("absoluteExpiresAt").isTextual());
        assertEquals("2026-08-19T14:00:00Z", payload.get("absoluteExpiresAt").textValue());

        Set<String> forbidden = new HashSet<>(Arrays.asList("ticket", "credential", "shell",
                "executable", "environment", "command"));
        assertFalse(containsForbiddenField(fixture, forbidden));

        assertEquals(fixture, serializeTerminalCommandFromService());
    }

    private JsonNode serializeTerminalCommandFromService() throws Exception {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        ProcessingAgentCommandMapper commandMapper = mock(ProcessingAgentCommandMapper.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId("11111111-1111-4111-8111-111111111111");
        when(commandMapper.selectEnabledAgentForUpdate(agent.getAgentId()))
                .thenReturn(agent.getAgentId());
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        AgentCommandService service = new AgentCommandService(commandMapper, mapper,
                mock(AgentAuditService.class), Clock.fixed(now, ZoneOffset.UTC), null,
                "wss://management.example/terminal/v1/agent", environment,
                mock(ApplicationEventPublisher.class));

        service.requestTerminalCommand(agent, "44444444-4444-4444-8444-444444444444",
                Instant.parse("2026-08-19T12:01:30Z"),
                Instant.parse("2026-08-19T14:00:00Z"), 7, "SUPER_ADMIN");
        ArgumentCaptor<ProcessingAgentCommandRecord> saved =
                ArgumentCaptor.forClass(ProcessingAgentCommandRecord.class);
        verify(commandMapper).insert(saved.capture());
        ProcessingAgentCommandRecord record = saved.getValue();
        record.setCommandId("77777777-7777-4777-8777-777777777777");
        record.setLeaseToken("66666666-6666-4666-8666-666666666666");
        record.setLeaseExpiresAt(LocalDateTime.ofInstant(
                Instant.parse("2026-08-19T12:05:00Z"), ZoneOffset.UTC));

        Method toEnvelope = AgentCommandService.class.getDeclaredMethod(
                "toEnvelope", ProcessingAgentCommandRecord.class);
        toEnvelope.setAccessible(true);
        AgentCommandEnvelope emitted = (AgentCommandEnvelope) toEnvelope.invoke(service, record);
        return mapper.valueToTree(emitted);
    }

    private Set<String> fieldNames(JsonNode object) {
        Set<String> names = new HashSet<>();
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            names.add(fields.next());
        }
        return names;
    }

    private boolean containsForbiddenField(JsonNode node, Set<String> forbidden) {
        if (node.isObject()) {
            Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                java.util.Map.Entry<String, JsonNode> field = fields.next();
                if (forbidden.contains(field.getKey())
                        || containsForbiddenField(field.getValue(), forbidden)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsForbiddenField(child, forbidden)) {
                    return true;
                }
            }
        }
        return false;
    }
}
