package com.match.registry.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.registry.persistence.*;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SimpleImageDeploymentTest {
    @Test public void enabledFileDeploysWithoutReleaseOrDigest() {
        ImageFileMapper files=mock(ImageFileMapper.class); ImageReleaseMapper releases=mock(ImageReleaseMapper.class);
        ImageDeploymentMapper deployments=mock(ImageDeploymentMapper.class); ProcessingAgentMapper agents=mock(ProcessingAgentMapper.class);
        AgentCommandService commands=mock(AgentCommandService.class); ImageArtifactMapper artifacts=mock(ImageArtifactMapper.class);
        ImageFileRecord file=new ImageFileRecord(); file.setFileId("file-1"); file.setEnabled(true); file.setImageRepository("xkp/anno"); file.setImageTag("v1");
        ProcessingAgentRecord agent=new ProcessingAgentRecord(); agent.setAgentId("agent-1"); agent.setEnabled(true);
        when(files.selectById("file-1")).thenReturn(file); when(agents.selectForManagement("agent-1")).thenReturn(agent);
        AgentCommandView command=new AgentCommandView(); command.setCommandId("command-1");
        when(commands.requestImageFileDeploymentCommand(any(), anyString(), anyBoolean(), anyString(), anyString(), any(), anyString())).thenReturn(command);
        ImageDeploymentService service=new ImageDeploymentService(releases, artifacts, deployments, agents, commands, files,
                Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));

        ImageDeploymentRecord result=service.deployFile("SUPER_ADMIN", "file-1", "agent-1", true, "request-1", 7);

        assertEquals("xkp/anno:v1", result.getTargetImage()); assertEquals("file-1", result.getFileId());
        verify(commands).requestImageFileDeploymentCommand(eq(agent), eq("xkp/anno:v1"), eq(true), anyString(), eq("request-1"), eq(7), eq("SUPER_ADMIN"));
        verify(releases, never()).selectById(anyString());
    }

    @Test public void activeDeploymentReturnsChineseServerAndImageMessage() {
        ImageFileMapper files = mock(ImageFileMapper.class); ImageReleaseMapper releases = mock(ImageReleaseMapper.class);
        ImageDeploymentMapper deployments = mock(ImageDeploymentMapper.class); ProcessingAgentMapper agents = mock(ProcessingAgentMapper.class);
        AgentCommandService commands = mock(AgentCommandService.class); ImageArtifactMapper artifacts = mock(ImageArtifactMapper.class);
        ImageFileRecord file = new ImageFileRecord(); file.setFileId("file-1"); file.setEnabled(true); file.setImageRepository("xkp/anno"); file.setImageTag("v1");
        ProcessingAgentRecord agent = new ProcessingAgentRecord(); agent.setAgentId("agent-1"); agent.setDisplayName("xt-01"); agent.setEnabled(true);
        ImageDeploymentRecord active = new ImageDeploymentRecord(); active.setTargetImage("xkp/code:v1");
        when(files.selectById("file-1")).thenReturn(file); when(agents.selectForManagement("agent-1")).thenReturn(agent);
        when(deployments.selectActiveSlotForUpdate("agent-1:DIRECT")).thenReturn(active);
        ImageDeploymentService service = new ImageDeploymentService(releases, artifacts, deployments, agents, commands, files,
                Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
        try { service.deployFile("SUPER_ADMIN", "file-1", "agent-1", true, "request-1", 7); org.junit.Assert.fail(); }
        catch (IllegalArgumentException error) {
            assertEquals("xt-01 正在下发 xkp/code:v1，请等待当前任务完成后再下发其他镜像", error.getMessage());
        }
    }
}
