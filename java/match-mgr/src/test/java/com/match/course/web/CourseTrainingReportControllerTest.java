package com.match.course.web;

import com.match.course.service.CourseTrainingReportService;
import com.match.entity.User;
import com.match.security.RoleGuard;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CourseTrainingReportControllerTest {
    @Test
    public void businessAdminCanUseOwnDemoReport() {
        RoleGuard roles = mock(RoleGuard.class);
        CourseTrainingReportService service = mock(CourseTrainingReportService.class);
        User admin = new User(); admin.setUserId(9);
        when(roles.requireBusinessUser()).thenReturn(admin);

        new CourseTrainingReportController(roles, service).get("course-1");

        verify(service).get(9, "course-1");
    }
}
