package com.match.controller;

import com.match.entity.Subject;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.environment.service.EnvironmentOperationService;
import com.match.security.AdminGuard;
import com.match.service.impl.SubjectManagementService;
import com.match.util.result.ResponseResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdminParticipantPreviewControllerTest {
    @Mock private SubjectManagementService subjectService;
    @Mock private EnvironmentOperationService environmentService;
    @Mock private AdminGuard adminGuard;

    private AdminParticipantPreviewController controller;

    @Before
    public void setUp() {
        controller = new AdminParticipantPreviewController(adminGuard, subjectService, environmentService);
    }

    @Test
    public void paperPreviewReturnsOnlyParticipantSafeQuestionFields() {
        Subject subject = new Subject();
        subject.setSubjectId(41);
        subject.setSubjectName("题目");
        subject.setSubjectType("single_choice");
        subject.setOptions(Arrays.asList("A", "B"));
        subject.setCorrectAnswer("A");
        subject.setSubjectIdentification("internal-key");
        subject.setVscodeUrl("http://internal/editor");
        subject.setScore(20);
        subject.setAnswering("管理员答题说明");
        subject.setModular("A");
        subject.setModularName("模块 A");
        when(subjectService.list("A", null, null)).thenReturn(Arrays.asList(subject));

        ResponseResult<Object> response = controller.subjects("A");
        List<?> records = (List<?>) response.getData();
        Map<?, ?> record = (Map<?, ?>) records.get(0);
        Map<?, ?> projected = (Map<?, ?>) record.get("subject");

        verify(adminGuard).requireAdmin();
        assertEquals("题目", projected.get("subjectName"));
        assertEquals(Arrays.asList("A", "B"), projected.get("options"));
        assertFalse(projected.containsKey("subjectId"));
        assertFalse(projected.containsKey("correctAnswer"));
        assertFalse(projected.containsKey("subjectIdentification"));
        assertFalse(projected.containsKey("vscodeUrl"));
        assertFalse(projected.containsKey("score"));
        assertFalse(projected.containsKey("answering"));
        assertNull(record.get("answerSheet"));
    }

    @Test
    public void environmentPreviewsOmitIdentifiersAndConfiguration() {
        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setEnvironmentId("internal-environment");
        environment.setUserId(21);
        environment.setCourseId(7);
        environment.setActualState("RUNNING");
        environment.setAnnotationContainerName("internal-container");
        when(environmentService.listAll()).thenReturn(Arrays.asList(environment));

        List<?> training = (List<?>) controller.trainingEnvironments().getData();
        Map<?, ?> row = (Map<?, ?>) training.get(0);
        Map<?, ?> competition = (Map<?, ?>) controller.competitionEnvironment().getData();

        assertEquals("课程环境 1", row.get("courseLabel"));
        assertEquals("RUNNING", row.get("actualState"));
        assertFalse(row.containsKey("courseId"));
        assertFalse(row.containsKey("environmentId"));
        assertFalse(row.containsKey("userId"));
        assertFalse(row.containsKey("annotationContainerName"));
        assertEquals("UNBOUND", competition.get("readiness"));
        assertEquals(1, competition.size());
        verify(adminGuard, org.mockito.Mockito.times(2)).requireAdmin();
        verify(environmentService).listAll();
    }
}
