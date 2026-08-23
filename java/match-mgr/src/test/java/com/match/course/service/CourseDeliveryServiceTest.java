package com.match.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.service.AgentCommandService;
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

public class CourseDeliveryServiceTest {
    private final CourseResourceMapper resources = mock(CourseResourceMapper.class);
    private final TrainingEnvironmentMapper environments = mock(TrainingEnvironmentMapper.class);
    private final CourseDeliveryService service = new CourseDeliveryService(resources,
            mock(CourseDeliveryMapper.class), environments, mock(ProcessingAgentMapper.class),
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
}
