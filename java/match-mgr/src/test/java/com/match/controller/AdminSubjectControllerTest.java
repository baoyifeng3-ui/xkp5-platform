package com.match.controller;

import com.match.security.AdminAccessException;
import com.match.security.AdminGuard;
import com.match.service.impl.SubjectManagementService;
import com.match.util.result.ResponseResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdminSubjectControllerTest {
    @Mock
    private SubjectManagementService subjectService;

    @Mock
    private AdminGuard adminGuard;

    private AdminSubjectController controller;

    @Before
    public void setUp() {
        controller = new AdminSubjectController(subjectService, adminGuard);
    }

    @Test
    public void clearsAnswersAfterAdminCheck() {
        when(subjectService.clearAnswers()).thenReturn(4);

        ResponseResult<Object> result = controller.clearAnswers();

        assertEquals(200, result.getCode());
        assertEquals(4, result.getData());
        verify(adminGuard).requireAdmin();
    }

    @Test
    public void clearsAllQuestionsAfterAdminCheck() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("answerCount", 4);
        counts.put("subjectCount", 3);
        when(subjectService.clearAll()).thenReturn(counts);

        ResponseResult<Object> result = controller.clearAll();

        assertEquals(200, result.getCode());
        assertEquals(counts, result.getData());
        verify(adminGuard).requireAdmin();
    }

    @Test
    public void rejectsClearingWhenAdminCheckFails() {
        doThrow(new AdminAccessException("仅管理员可以执行此操作"))
                .when(adminGuard).requireAdmin();

        try {
            controller.clearAll();
            fail("非管理员不应清空题目");
        } catch (AdminAccessException exception) {
            assertEquals("仅管理员可以执行此操作", exception.getMessage());
        }

        verify(subjectService, never()).clearAll();
    }
}
