package com.match.registry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.registry.persistence.ImageArtifactMapper;
import com.match.registry.persistence.ImageArtifactRecord;
import com.match.registry.persistence.ImageDeploymentMapper;
import com.match.registry.persistence.ImageDeploymentRecord;
import com.match.registry.persistence.ImageReleaseMapper;
import com.match.registry.persistence.ImageReleaseRecord;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ImageDeploymentServiceTest {
    private static final String DIGEST = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private ImageReleaseMapper releases;
    private ImageArtifactMapper artifacts;
    private ImageDeploymentMapper deployments;
    private ProcessingAgentMapper agents;
    private AgentCommandService commands;
    private ImageDeploymentService service;

    @Before
    public void setUp() {
        releases = mock(ImageReleaseMapper.class);
        artifacts = mock(ImageArtifactMapper.class);
        deployments = mock(ImageDeploymentMapper.class);
        agents = mock(ProcessingAgentMapper.class);
        commands = mock(AgentCommandService.class);
        service = new ImageDeploymentService(releases, artifacts, deployments, agents, commands,
                Clock.fixed(Instant.parse("2026-08-21T08:09:10Z"), ZoneOffset.UTC));
    }

    @Test
    public void deploymentPersistsDesiredDigestAndSendsOnlyImmutablePayload() throws Exception {
        ImageReleaseRecord release = release("EDITOR");
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId("agent-1"); agent.setEnabled(true);
        when(releases.selectById("release-1")).thenReturn(release);
        when(agents.selectForManagement("agent-1")).thenReturn(agent);
        when(deployments.selectActiveForUpdate("agent-1", "EDITOR")).thenReturn(null);
        when(commands.requestImageDeploymentCommand(eq(agent), eq("EDITOR"), eq(DIGEST),
                eq("UPDATE_CONTAINERS"), eq("request-1"), eq(7), eq("SUPER_ADMIN")))
                .thenReturn(command());

        ImageDeploymentRecord result = service.deploy("SUPER_ADMIN", "release-1", "agent-1",
                "EDITOR", null, "request-1", true, 7);

        assertEquals(DIGEST, result.getTargetDigest());
        assertEquals("UPDATE_CONTAINERS", result.getUpdatePolicy());
        verify(deployments).insert(any(ImageDeploymentRecord.class));
        verify(commands).requestImageDeploymentCommand(eq(agent), eq("EDITOR"), eq(DIGEST),
                eq("UPDATE_CONTAINERS"), eq("request-1"), eq(7), eq("SUPER_ADMIN"));
    }

    @Test
    public void imageOnlyDoesNotRequireContainerConfirmation() {
        ImageReleaseRecord release = release("ANNOTATION");
        ProcessingAgentRecord agent = new ProcessingAgentRecord(); agent.setAgentId("agent-1"); agent.setEnabled(true);
        when(releases.selectById("release-1")).thenReturn(release);
        when(agents.selectForManagement("agent-1")).thenReturn(agent);
        when(deployments.selectActiveForUpdate("agent-1", "ANNOTATION")).thenReturn(null);
        when(commands.requestImageDeploymentCommand(any(ProcessingAgentRecord.class), eq("ANNOTATION"), eq(DIGEST),
                eq("IMAGE_ONLY"), eq("request-2"), eq(7), eq("SUPER_ADMIN"))).thenReturn(command());

        assertEquals("IMAGE_ONLY", service.deploy("SUPER_ADMIN", "release-1", "agent-1",
                "ANNOTATION", "IMAGE_ONLY", "request-2", false, 7).getUpdatePolicy());
    }

    @Test
    public void repeatedRequestReturnsExistingDeploymentWithoutDispatchingAgain() {
        ImageDeploymentRecord existing = new ImageDeploymentRecord();
        existing.setDeploymentId("deployment-1"); existing.setActiveDeploymentKey("agent-1:EDITOR:request-1");
        existing.setTargetDigest(DIGEST); existing.setState("PENDING");
        when(deployments.selectActiveForUpdate("agent-1", "EDITOR")).thenReturn(existing);

        assertSame(existing, service.deploy("SUPER_ADMIN", "release-1", "agent-1", "EDITOR",
                null, "request-1", true, 7));
        verify(releases, never()).selectById(anyString());
        verify(commands, never()).requestImageDeploymentCommand(any(), anyString(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    public void updateContainersRequiresExplicitConfirmation() {
        try {
            service.deploy("SUPER_ADMIN", "release-1", "agent-1", "EDITOR", null,
                    "request-1", false, 7);
        } catch (IllegalArgumentException expected) {
            assertEquals("EXPLICIT_CONFIRMATION_REQUIRED", expected.getMessage());
        }
    }

    private ImageReleaseRecord release(String component) {
        ImageReleaseRecord r = new ImageReleaseRecord(); r.setReleaseId("release-1");
        r.setComponentType(component); r.setRegistryDigest(DIGEST); r.setState("PUBLISHED");
        return r;
    }

    private AgentCommandView command() { AgentCommandView v = new AgentCommandView(); v.setCommandId("command-1"); return v; }
}
