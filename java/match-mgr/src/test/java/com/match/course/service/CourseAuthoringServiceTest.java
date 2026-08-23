package com.match.course.service;

import com.match.course.model.CourseResourceRequest;
import com.match.course.model.CourseUpsertRequest;
import com.match.course.model.ResourceView;
import com.match.course.persistence.CourseMapper;
import com.match.course.persistence.CourseRecord;
import com.match.course.persistence.CourseResourceMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseAuthoringServiceTest {
    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final CourseResourceMapper resourceMapper = mock(CourseResourceMapper.class);
    private final CourseAuthoringService service = new CourseAuthoringService(courseMapper, resourceMapper);

    @Test
    void createsDisabledCourseUntilAdministratorEnablesIt() {
        CourseUpsertRequest request = course("视觉检测", "人工智能");

        assertEquals(false, service.create(request, 7).getCourse().getEnabled());
        verify(courseMapper).insert(any(CourseRecord.class));
    }

    @Test
    void rejectsUnsupportedResourceType() {
        CourseRecord course = new CourseRecord();
        course.setCourseId("course-1");
        when(courseMapper.selectForUpdate("course-1")).thenReturn(course);
        CourseResourceRequest request = resource("EXE");

        try {
            service.addResource("course-1", request, 7);
            fail("unsupported resource type should be rejected");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("资源类型"));
        }
    }

    @Test
    void resourceProjectionNeverExposesStorageKeyOrDigest() {
        CourseRecord course = new CourseRecord();
        course.setCourseId("course-1");
        when(courseMapper.selectForUpdate("course-1")).thenReturn(course);

        ResourceView view = service.addResource("course-1", resource("ARCHIVE"), 7);

        assertEquals("ARCHIVE", view.getResourceType());
        assertTrue(java.util.Arrays.stream(ResourceView.class.getDeclaredFields())
                .noneMatch(field -> "storageKey".equals(field.getName()) || "sha256".equals(field.getName())));
    }

    @Test
    void togglesResourceAvailability() {
        com.match.course.persistence.CourseResourceRecord resource = new com.match.course.persistence.CourseResourceRecord();
        resource.setResourceId("resource-1");
        resource.setEnabled(true);
        when(resourceMapper.selectById("resource-1")).thenReturn(resource);
        service.setResourceEnabled("resource-1", false);
        assertEquals(false, resource.getEnabled());
        verify(resourceMapper).updateById(resource);
    }

    private CourseUpsertRequest course(String name, String type) {
        CourseUpsertRequest request = new CourseUpsertRequest();
        request.setName(name);
        request.setCourseType(type);
        return request;
    }

    private CourseResourceRequest resource(String type) {
        CourseResourceRequest request = new CourseResourceRequest();
        request.setResourceType(type);
        request.setName("训练数据");
        request.setStorageKey("private/course/archive.zip");
        request.setSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        return request;
    }
}
