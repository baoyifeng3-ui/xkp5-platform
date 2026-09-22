package com.match.course.web;

import com.match.course.persistence.*;
import com.match.course.service.CourseLearningService;
import com.match.security.RoleGuard;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CourseVisibilityRegressionTest {
    @Test public void unpublishedAndMissingCoursesCannotExposeChapters() {
        RoleGuard roles = mock(RoleGuard.class);
        CourseMapper courses = mock(CourseMapper.class);
        CourseChapterMapper chapters = mock(CourseChapterMapper.class);
        UserCourseController controller = new UserCourseController(roles, mock(CourseLearningService.class));
        controller.setChapterMapper(chapters);
        controller.setCourseMapper(courses);
        CourseRecord draft = new CourseRecord();
        draft.setEnabled(false);
        when(courses.selectById("draft")).thenReturn(draft);
        for (String id : new String[]{"draft", "missing"}) {
            try { controller.chapters(id); fail("Hidden course exposed"); }
            catch (IllegalArgumentException expected) { }
        }
        verifyZeroInteractions(chapters);
        draft.setEnabled(true);
        controller.chapters("draft");
        verify(chapters).selectByCourse("draft");
    }
}
