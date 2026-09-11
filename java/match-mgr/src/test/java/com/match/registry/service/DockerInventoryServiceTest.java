package com.match.registry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.*;
import com.match.agent.service.AgentCommandService;
import com.match.environment.model.TrainingEnvironmentOperationView;
import com.match.environment.persistence.*;
import com.match.environment.service.EnvironmentOperationService;
import org.junit.Before;
import org.junit.Test;
import java.time.*;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DockerInventoryServiceTest {
    private final String imageId="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private final ProcessingAgentMapper agents=mock(ProcessingAgentMapper.class);
    private final ProcessingAgentCommandMapper records=mock(ProcessingAgentCommandMapper.class);
    private final AgentCommandService commands=mock(AgentCommandService.class);
    private final ContainerTemplateMapper templates=mock(ContainerTemplateMapper.class);
    private final TrainingEnvironmentMapper environments=mock(TrainingEnvironmentMapper.class);
    private final EnvironmentOperationService operations=mock(EnvironmentOperationService.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-09-09T08:00:00Z"),ZoneOffset.UTC);
    private final DockerInventoryService service=new DockerInventoryService(agents,records,commands,templates,environments,operations,new ObjectMapper(),clock);
    private ProcessingAgentRecord agent;
    @Before public void setup() {
        agent=new ProcessingAgentRecord(); agent.setAgentId("a"); agent.setEnabled(true);
        agent.setLastSeenAt(LocalDateTime.now(clock));
        agent.setLatestMetrics("{\"dockerAvailable\":true,\"images\":[{\"repository\":\"xkp/a\",\"tag\":\"latest\",\"id\":\""+imageId+"\"}],\"containers\":[{\"name\":\"training\",\"id\":\"123abc\"}]}");
        when(agents.selectForManagement("a")).thenReturn(agent);
        when(records.selectEnabledAgentForUpdate("a")).thenReturn("a");
    }
    @Test public void templateReferenceBlocksImageDeletion() {
        when(templates.countImageReferences("xkp/a:latest",imageId)).thenReturn(1);
        expectInvalid(() -> service.deleteImage("a","xkp/a:latest",imageId,7));
        verifyZeroInteractions(commands);
    }
    @Test public void imageListDoesNotRequireAnyContainers() {
        agent.setLatestMetrics("{\"dockerAvailable\":true,\"images\":[{\"repository\":\"xkp/anno\",\"tag\":\"v1\",\"id\":\""+imageId+"\"}],\"containers\":null}");
        assertEquals(1, service.images("a").size());
        assertEquals("xkp/anno", service.images("a").get(0).getRepository());
    }
    @Test public void changedImageIdentityCannotDeleteReplacement() {
        expectInvalid(() -> service.deleteImage("a","xkp/a:latest","sha256:bbbb",7));
        verifyZeroInteractions(commands);
    }
    @Test public void managedContainerReusesEnvironmentDeleteInsteadOfDeletingDatabaseFirst() {
        TrainingEnvironmentRecord env=new TrainingEnvironmentRecord(); env.setEnvironmentId("env"); env.setAnnotationContainerName("training");
        when(environments.selectByAgentForUpdate("a")).thenReturn(Collections.singletonList(env));
        TrainingEnvironmentOperationView operation=new TrainingEnvironmentOperationView(); operation.setCommand(new AgentCommandView());
        when(operations.delete("env",7,"SUPER_ADMIN")).thenReturn(operation);
        assertSame(operation.getCommand(),service.deleteContainer("a","training","123abc",7));
        verify(environments,never()).deleteById(anyString()); verifyZeroInteractions(commands);
    }
    @Test public void inspectionMustBelongToSameActorServerAndTargetAndBeRecent() {
        ProcessingAgentCommandRecord command=new ProcessingAgentCommandRecord();
        command.setAgentId("a"); command.setRequesterUserId(7); command.setCommandType("DOCKER_INVENTORY_ACTION");
        command.setState("SUCCEEDED"); command.setCompletedAt(LocalDateTime.now(clock)); command.setRequestedAt(LocalDateTime.now(clock));
        command.setPayloadJson("{\"action\":\"INSPECT_IMAGE\",\"target\":\"xkp/a:latest\"}");
        command.setResultJson("{\"id\":\""+imageId+"\",\"repository\":\"xkp/a\",\"tag\":\"latest\",\"created\":10}");
        when(records.selectById("c")).thenReturn(command);
        assertEquals(imageId,service.inspectedImage("a","c","xkp/a:latest",7).getId());
        expectInvalid(() -> service.inspectedImage("a","c","xkp/a:latest",8));
        expectInvalid(() -> service.inspectedImage("a","c","xkp/b:latest",7));
        when(records.countImageDeletesSince(eq("a"),any())).thenReturn(1);
        expectInvalid(() -> service.inspectedImage("a","c","xkp/a:latest",7));
    }
    private void expectInvalid(Runnable action) { try { action.run(); fail("accepted invalid action"); } catch (IllegalArgumentException expected) {} }
}
