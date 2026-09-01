package com.match.resource.service;

import com.match.course.persistence.CourseMapper;
import com.match.resource.persistence.CourseResourceLinkMapper;
import com.match.resource.persistence.PlatformFileMapper;
import com.match.resource.persistence.PlatformFileRecord;
import com.match.resource.persistence.ResourceCleanupAuditMapper;
import com.match.resource.persistence.ResourceDirectoryMapper;
import com.match.security.AdminAccessException;
import com.match.security.UserRole;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

public class ResourceSpaceServiceTest {
    @Test
    public void homeworkListingAlwaysUsesCurrentUserScope() {
        Fixture fixture = fixture();
        fixture.service.entries(ResourceSpaceType.HOMEWORK, UserRole.USER, 20, null);
        verify(fixture.directories).selectChildren("HOMEWORK", 20, null);
        verify(fixture.files).selectEntries("HOMEWORK", 20, null);
    }

    @Test
    public void courseUploadRequiresAtLeastOneCourseBeforeStorage() {
        Fixture fixture = fixture();
        try {
            fixture.service.upload(ResourceSpaceType.COURSE, UserRole.ADMIN, 10, null,
                    new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[]{1}),
                    Collections.emptyList(), "EBOOK");
            fail("course-less upload was accepted");
        } catch (IllegalArgumentException expected) {
            verify(fixture.storage, never()).store(any());
        }
    }

    @Test
    public void exchangeUserCannotDeleteAnotherUsersFile() {
        Fixture fixture = fixture();
        PlatformFileRecord file = new PlatformFileRecord();
        file.setFileId("file-1"); file.setSpaceType("EXCHANGE"); file.setUploadedBy(21);
        when(fixture.files.selectScoped("file-1", "EXCHANGE", null)).thenReturn(file);
        try {
            fixture.service.deleteFile(ResourceSpaceType.EXCHANGE, UserRole.USER, 20, "file-1");
            fail("another user's file was deleted");
        } catch (AdminAccessException expected) {
            verify(fixture.files, never()).deleteById(eq("file-1"));
        }
    }

    @Test
    public void storageFailureUsesServiceUnavailableChineseError() {
        Fixture fixture = fixture();
        when(fixture.storage.store(any())).thenThrow(new ResourceOperationException(
                503, "STORAGE_UNAVAILABLE", "文件存储服务不可用，请稍后重试"));
        try {
            fixture.service.upload(ResourceSpaceType.PUBLIC, UserRole.ADMIN, 10, null,
                    new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[]{1}),
                    Collections.emptyList(), "ARCHIVE");
            fail("storage outage was accepted");
        } catch (ResourceOperationException expected) {
            assertEquals(503, expected.getStatus());
            assertEquals("文件存储服务不可用，请稍后重试", expected.getMessage());
        }
    }

    private Fixture fixture() {
        ResourceDirectoryMapper directories = mock(ResourceDirectoryMapper.class);
        PlatformFileMapper files = mock(PlatformFileMapper.class);
        CourseResourceLinkMapper links = mock(CourseResourceLinkMapper.class);
        ResourceCleanupAuditMapper audits = mock(ResourceCleanupAuditMapper.class);
        CourseMapper courses = mock(CourseMapper.class);
        ResourceStorageService storage = mock(ResourceStorageService.class);
        ResourceSpaceService service = new ResourceSpaceService(directories, files, links, audits,
                courses, storage, new ResourceAccessPolicy());
        return new Fixture(service, directories, files, storage);
    }

    private static class Fixture {
        final ResourceSpaceService service; final ResourceDirectoryMapper directories;
        final PlatformFileMapper files; final ResourceStorageService storage;
        Fixture(ResourceSpaceService service, ResourceDirectoryMapper directories,
                PlatformFileMapper files, ResourceStorageService storage) {
            this.service = service; this.directories = directories; this.files = files; this.storage = storage;
        }
    }
}
