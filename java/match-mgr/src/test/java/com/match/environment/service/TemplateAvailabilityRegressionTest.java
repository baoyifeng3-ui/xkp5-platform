package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.persistence.*;
import com.match.agent.service.AgentCommandService;
import com.match.environment.model.ContainerTemplateRequest;
import com.match.environment.persistence.*;
import com.match.licensing.guard.LicenseGuard;
import com.match.registry.persistence.*;
import com.match.registry.service.DockerInventoryService;
import org.junit.Test;
import java.time.*;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TemplateAvailabilityRegressionTest {
    @Test public void historicalPushWithoutCurrentServerImageIsRejected() {
        ContainerTemplateMapper templates = mock(ContainerTemplateMapper.class);
        ProcessingAgentMapper agents = mock(ProcessingAgentMapper.class);
        ImageFileMapper files = mock(ImageFileMapper.class);
        ImageDeploymentMapper deployments = mock(ImageDeploymentMapper.class);
        ContainerTemplateService service = new ContainerTemplateService(templates, mock(LicenseGuard.class), Clock.systemUTC());
        service.setImageFileMapper(files); service.setImageDeploymentMapper(deployments);
        service.setDockerInventoryService(new DockerInventoryService(agents, mock(ProcessingAgentCommandMapper.class),
                mock(AgentCommandService.class), templates, mock(TrainingEnvironmentMapper.class),
                mock(EnvironmentOperationService.class), new ObjectMapper(), Clock.systemUTC()));
        ImageFileRecord file = new ImageFileRecord(); file.setFileId("current"); file.setEnabled(true);
        when(files.selectByImageReference("xkp/test:latest")).thenReturn(file);
        ImageDeploymentRecord pushed = new ImageDeploymentRecord(); pushed.setAgentId("server");
        when(deployments.selectLatestSucceededByFileId("current")).thenReturn(pushed);
        ProcessingAgentRecord server = new ProcessingAgentRecord(); server.setAgentId("server"); server.setEnabled(true);
        server.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC)); server.setLatestMetrics("{\"dockerAvailable\":true,\"images\":[]}");
        when(agents.selectVisibleAgents()).thenReturn(Collections.singletonList(server));
        when(agents.selectForManagement("server")).thenReturn(server);
        ContainerTemplateRequest r = new ContainerTemplateRequest(); r.setTemplateName("test"); r.setComponentType("ANNOTATION");
        r.setImageReference("xkp/test:latest"); r.setRuntimeName("sysbox-runc"); r.setMountTarget("/root/data"); r.setRestartPolicy("always");
        try { service.publish(r, 7); fail("historical success cannot prove an image still exists"); }
        catch (IllegalArgumentException expected) { verify(templates, never()).insert(any(ContainerTemplateRecord.class)); }
    }
}
