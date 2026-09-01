package com.match.environment.service;

import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.environment.persistence.ActiveClassSessionMapper;
import com.match.environment.persistence.ActiveClassSessionRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.mapper.UserMapper;
import org.junit.Test;

import java.util.Map;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ActiveClassSessionServiceTest {
    @Test
    public void userPolicyExposesOnlyTrainingAndValidationDuringClass() {
        ActiveClassSessionMapper sessions = mock(ActiveClassSessionMapper.class);
        ActiveClassSessionRecord session = new ActiveClassSessionRecord();
        session.setActive(true); session.setCourseId("course-1"); session.setEditorTool("JUPYTER");
        when(sessions.selectCurrent()).thenReturn(session);
        ActiveClassSessionService service = new ActiveClassSessionService(sessions,
                mock(TrainingEnvironmentMapper.class), mock(EnvironmentOperationService.class),
                mock(ProcessingAgentMapper.class), mock(UserMapper.class));

        Map<String, Object> policy = service.userPolicy();

        assertEquals(true, policy.get("active"));
        assertEquals("course-1", policy.get("courseId"));
        assertEquals("JUPYTER", policy.get("editorTool"));
        assertEquals(Arrays.asList("/training-environment", "/training-validation"), policy.get("allowedRoutes"));
    }
    @Test
    public void inactiveStatusDoesNotQueryUsersWithAnEmptyIdList() {
        ActiveClassSessionMapper sessions = mock(ActiveClassSessionMapper.class);
        TrainingEnvironmentMapper environments = mock(TrainingEnvironmentMapper.class);
        ProcessingAgentMapper agents = mock(ProcessingAgentMapper.class);
        UserMapper users = mock(UserMapper.class);
        ActiveClassSessionRecord session = new ActiveClassSessionRecord();
        session.setActive(false);
        when(sessions.selectCurrent()).thenReturn(session);
        ActiveClassSessionService service = new ActiveClassSessionService(sessions, environments,
                mock(EnvironmentOperationService.class), agents, users);

        Map<String, Object> status = service.status();

        assertEquals(false, status.get("active"));
        assertEquals(0, status.get("totalCount"));
        verify(users, never()).selectBatchIds(org.mockito.ArgumentMatchers.anyCollection());
    }
}
