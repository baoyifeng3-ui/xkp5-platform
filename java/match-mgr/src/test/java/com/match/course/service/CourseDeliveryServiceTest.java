package com.match.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.service.AgentCommandService;
import com.match.agent.model.AgentCommandFinishedEvent;
import com.match.course.persistence.CourseDeliveryMapper;
import com.match.course.persistence.CourseResourceMapper;
import com.match.course.persistence.CourseResourceRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class CourseDeliveryServiceTest {
    private final CourseResourceMapper resources = mock(CourseResourceMapper.class);
    private final CourseDeliveryMapper deliveries = mock(CourseDeliveryMapper.class);
    private final TrainingEnvironmentMapper environments = mock(TrainingEnvironmentMapper.class);
    private final CourseDeliveryService service = new CourseDeliveryService(resources,
            deliveries, environments, mock(ProcessingAgentMapper.class),
            mock(AgentCommandService.class), new ObjectMapper());

    @Test
    public void rejectsDisabledResource() {
        CourseResourceRecord resource = new CourseResourceRecord();
        resource.setEnabled(false);
        when(resources.selectById("resource-1")).thenReturn(resource);
        try {
            service.deliver("resource-1", "environment-1", 7, 7, "USER");
            fail("disabled resource should be rejected");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("停用"));
        }
    }

    @Test
    public void rejectsEnvironmentOwnedByAnotherUser() {
        CourseResourceRecord resource = new CourseResourceRecord();
        resource.setEnabled(true);
        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setUserId(8);
        when(resources.selectById("resource-1")).thenReturn(resource);
        when(environments.selectForUpdate("environment-1")).thenReturn(environment);
        try {
            service.deliver("resource-1", "environment-1", 7, 7, "USER");
            fail("foreign environment should be rejected");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("不属于目标用户"));
        }
    }

    @Test
    public void commandSuccessMovesDeliveryToSucceeded() {
        com.match.course.persistence.CourseDeliveryRecord delivery = new com.match.course.persistence.CourseDeliveryRecord();
        delivery.setDeliveryId("delivery-1");
        delivery.setCommandId("command-1");
        delivery.setState("DISPATCHED");
        when(deliveries.selectByCommandId("command-1")).thenReturn(delivery);
        service.onCommandFinished(new AgentCommandFinishedEvent("command-1", "agent-1",
                "DELIVER_COURSE_RESOURCE", true, "RESOURCE_DELIVERED", "ok"));
        assertTrue("SUCCEEDED".equals(delivery.getState()));
        verify(deliveries).updateById(delivery);
    }

    @Test
    public void commandFailureMovesDeliveryToFailedWithReason() {
        com.match.course.persistence.CourseDeliveryRecord delivery = new com.match.course.persistence.CourseDeliveryRecord();
        delivery.setDeliveryId("delivery-2");
        delivery.setCommandId("command-2");
        delivery.setState("DISPATCHED");
        when(deliveries.selectByCommandId("command-2")).thenReturn(delivery);
        service.onCommandFinished(new AgentCommandFinishedEvent("command-2", "agent-1",
                "DELIVER_COURSE_RESOURCE", false, "RESOURCE_COPY_FAILED", "disk full"));
        assertTrue("FAILED".equals(delivery.getState()));
        assertTrue("RESOURCE_COPY_FAILED".equals(delivery.getFailureCode()));
        assertTrue("disk full".equals(delivery.getFailureMessage()));
        verify(deliveries).updateById(delivery);
    }
}
