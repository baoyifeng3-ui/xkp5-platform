package com.match.course.web;

import com.match.course.service.CourseAuthoringService;
import com.match.security.RoleGuard;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class CourseContentImageControllerTest {
    @Test
    public void rejectsNonImageCourseContentUpload() throws Exception {
        AdminCourseController controller = new AdminCourseController(mock(RoleGuard.class), mock(CourseAuthoringService.class));
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "text".getBytes());
        try {
            AdminCourseController.class.getMethod("uploadContentImage", MultipartFile.class).invoke(controller, file);
            fail("非图片文件不应作为课程内容图片上传");
        } catch (NoSuchMethodException error) {
            fail("缺少课程内容图片上传接口");
        } catch (InvocationTargetException error) {
            assertTrue(error.getCause() instanceof IllegalArgumentException);
            assertTrue(error.getCause().getMessage().contains("图片"));
        }
    }
}
