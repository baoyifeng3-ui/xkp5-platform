package com.match.course.service;

import com.match.course.persistence.CourseMapper;
import com.match.course.persistence.CourseProgressMapper;
import com.match.course.persistence.CourseResourceMapper;
import com.match.course.persistence.CourseResourceRecord;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import com.match.course.persistence.CourseProgressRecord;

public class CourseLearningServiceTest {
    private final CourseResourceMapper resources = mock(CourseResourceMapper.class);
    private final CourseProgressMapper progressMapper = mock(CourseProgressMapper.class);
    private final CourseLearningService service = new CourseLearningService(mock(CourseMapper.class), resources,
            progressMapper);

    @Test
    public void rejectsProgressForDisabledResource() {
        CourseResourceRecord resource = new CourseResourceRecord();
        resource.setEnabled(false);
        when(resources.selectById("resource-1")).thenReturn(resource);
        try {
            service.recordProgress(7, "resource-1", "PAGE", 20, false);
            fail("disabled resource should be rejected");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("停用"));
        }
    }

    @Test
    public void rejectsUnknownProgressKind() {
        CourseResourceRecord resource = new CourseResourceRecord();
        resource.setEnabled(true);
        when(resources.selectById("resource-1")).thenReturn(resource);
        try {
            service.recordProgress(7, "resource-1", "DOWNLOAD", 20, false);
            fail("unknown progress kind should be rejected");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("类型"));
        }
    }

    @Test
    public void rejectsArchivePreview() {
        CourseResourceRecord resource = new CourseResourceRecord();
        resource.setEnabled(true);
        resource.setResourceType("ARCHIVE");
        when(resources.selectById("archive-1")).thenReturn(resource);
        try {
            service.previewUrl("archive-1");
            fail("archive preview should be rejected");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("实训环境"));
        }
    }

    @Test
    public void calculatesProgressFromCurrentAndTotalValues() {
        CourseResourceRecord resource = new CourseResourceRecord();
        resource.setEnabled(true);
        when(resources.selectById("resource-1")).thenReturn(resource);
        service.recordProgress(7, "resource-1", "TIME", null, false, 45L, 90L, "00:45");
        ArgumentCaptor<CourseProgressRecord> saved = ArgumentCaptor.forClass(CourseProgressRecord.class);
        verify(progressMapper).upsertProgress(saved.capture());
        assertEquals(50, saved.getValue().getPercent().intValue());
        assertEquals("00:45", saved.getValue().getLastPosition());
    }
}
