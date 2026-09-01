package com.match.course.service;

import com.match.course.model.CourseResourceRequest;
import com.match.course.model.CourseResourceMetadataRequest;
import com.match.course.model.CourseChapterRequest;
import com.match.course.model.CourseUpsertRequest;
import com.match.course.model.ResourceView;
import com.match.course.persistence.CourseMapper;
import com.match.course.persistence.CourseRecord;
import com.match.course.persistence.CourseResourceMapper;
import com.match.course.persistence.CourseChapterMapper;
import com.match.course.persistence.CourseChapterRecord;
import com.match.resource.persistence.CourseResourceCatalogMapper;
import com.match.resource.persistence.CourseResourceLinkMapper;
import com.match.resource.persistence.CourseResourceLinkRecord;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanWrapperImpl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CourseAuthoringServiceTest {
    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final CourseResourceMapper resourceMapper = mock(CourseResourceMapper.class);
    private final CourseAuthoringService service = new CourseAuthoringService(courseMapper, resourceMapper);

    @Test
    public void createsDisabledCourseUntilAdministratorEnablesIt() {
        CourseUpsertRequest request = course("视觉检测", "人工智能");

        CourseRecord created = service.create(request, 7).getCourse();
        assertEquals(false, created.getEnabled());
        verify(courseMapper).insert(any(CourseRecord.class));
    }

    @Test
    public void sanitizesCourseIntroductionAndOutline() {
        CourseUpsertRequest request = course("视觉检测", "人工智能");
        BeanWrapperImpl input = new BeanWrapperImpl(request);
        input.setPropertyValue("introductionHtml", "<p onclick=\"bad()\"><font color=\"#ff0000\">简介</font></p><script>bad()</script>");
        input.setPropertyValue("outlineHtml", "<h2>大纲</h2><img src=\"/files/demo.png\" onerror=\"bad()\">");

        service.create(request, 7);

        ArgumentCaptor<CourseRecord> captor = ArgumentCaptor.forClass(CourseRecord.class);
        verify(courseMapper).insert(captor.capture());
        BeanWrapperImpl saved = new BeanWrapperImpl(captor.getValue());
        assertEquals("<p><font color=\"#ff0000\">简介</font></p>", saved.getPropertyValue("introductionHtml"));
        assertEquals("<h2>大纲</h2><img src=\"/files/demo.png\">", saved.getPropertyValue("outlineHtml"));
    }

    @Test
    public void rejectsUnsupportedResourceType() {
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
    public void rejectsInvalidResourceDigest() {
        CourseRecord course = new CourseRecord();
        course.setCourseId("course-1");
        when(courseMapper.selectForUpdate("course-1")).thenReturn(course);
        CourseResourceRequest request = resource("EBOOK");
        request.setSha256("invalid");
        try {
            service.addResource("course-1", request, 7);
            fail("invalid digest should be rejected");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("摘要"));
        }
    }

    @Test
    public void resourceProjectionNeverExposesStorageKeyOrDigest() {
        CourseRecord course = new CourseRecord();
        course.setCourseId("course-1");
        when(courseMapper.selectForUpdate("course-1")).thenReturn(course);

        ResourceView view = service.addResource("course-1", resource("ARCHIVE"), 7);

        assertEquals("ARCHIVE", view.getResourceType());
        assertTrue(java.util.Arrays.stream(ResourceView.class.getDeclaredFields())
                .noneMatch(field -> "storageKey".equals(field.getName()) || "sha256".equals(field.getName())));
    }

    @Test
    public void togglesResourceAvailability() {
        com.match.course.persistence.CourseResourceRecord resource = new com.match.course.persistence.CourseResourceRecord();
        resource.setResourceId("resource-1");
        resource.setEnabled(true);
        when(resourceMapper.selectById("resource-1")).thenReturn(resource);
        service.setResourceEnabled("resource-1", false);
        assertEquals(false, resource.getEnabled());
        verify(resourceMapper).updateById(resource);
    }

    @Test
    public void updatesChapterNameToolAndOrder() throws Exception {
        CourseResourceCatalogMapper catalogMapper = mock(CourseResourceCatalogMapper.class);
        CourseResourceLinkMapper linkMapper = mock(CourseResourceLinkMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseAuthoringService authoring = new CourseAuthoringService(courseMapper, resourceMapper, catalogMapper, linkMapper);
        authoring.setChapterMapper(chapterMapper);
        CourseRecord course = new CourseRecord(); course.setCourseId("course-1");
        CourseChapterRecord chapter = new CourseChapterRecord(); chapter.setChapterId("chapter-1"); chapter.setCourseId("course-1");
        when(courseMapper.selectForUpdate("course-1")).thenReturn(course);
        when(chapterMapper.selectById("chapter-1")).thenReturn(chapter);
        CourseChapterRequest request = new CourseChapterRequest(); request.setChapterName("第二章"); request.setPracticeTool("editor"); request.setSortOrder(20);

        CourseChapterRecord updated;
        try {
            updated = (CourseChapterRecord) CourseAuthoringService.class
                    .getMethod("updateChapter", String.class, String.class, CourseChapterRequest.class)
                    .invoke(authoring, "course-1", "chapter-1", request);
        } catch (NoSuchMethodException error) {
            fail("课程章节缺少更新接口"); return;
        }

        assertEquals("第二章", updated.getChapterName());
        assertEquals("EDITOR", updated.getPracticeTool());
        assertEquals(Integer.valueOf(20), updated.getSortOrder());
        verify(chapterMapper).updateById(chapter);
    }

    @Test
    public void refusesToDeleteChapterContainingResources() {
        CourseResourceCatalogMapper catalogMapper = mock(CourseResourceCatalogMapper.class);
        CourseResourceLinkMapper linkMapper = mock(CourseResourceLinkMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseAuthoringService authoring = new CourseAuthoringService(courseMapper, resourceMapper, catalogMapper, linkMapper);
        authoring.setChapterMapper(chapterMapper);
        CourseChapterRecord chapter = new CourseChapterRecord(); chapter.setChapterId("chapter-1"); chapter.setCourseId("course-1");
        CourseResourceLinkRecord link = new CourseResourceLinkRecord(); link.setChapterId("chapter-1");
        when(chapterMapper.selectById("chapter-1")).thenReturn(chapter);
        when(linkMapper.selectByCourse("course-1")).thenReturn(java.util.Collections.singletonList(link));

        try {
            authoring.deleteChapter("course-1", "chapter-1");
            fail("含资源章节不应直接删除");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("资源"));
        }
    }

    @Test
    public void exposesCourseResourceMetadataRequest() {
        try {
            Class<?> type = Class.forName("com.match.course.model.CourseResourceMetadataRequest");
            assertTrue(new BeanWrapperImpl(type.getDeclaredConstructor().newInstance()).isWritableProperty("name"));
        } catch (Exception error) {
            fail("缺少课程资源元数据请求类型: " + error.getClass().getSimpleName());
        }
    }

    @Test
    public void updatesCourseResourceMetadataWithoutRenamingSharedFile() throws Exception {
        CourseResourceCatalogMapper catalogMapper = mock(CourseResourceCatalogMapper.class);
        CourseResourceLinkMapper linkMapper = mock(CourseResourceLinkMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseAuthoringService authoring = new CourseAuthoringService(courseMapper, resourceMapper, catalogMapper, linkMapper);
        authoring.setChapterMapper(chapterMapper);
        CourseRecord course = new CourseRecord(); course.setCourseId("course-1");
        CourseChapterRecord chapter = new CourseChapterRecord(); chapter.setChapterId("chapter-1"); chapter.setCourseId("course-1");
        CourseResourceLinkRecord link = new CourseResourceLinkRecord(); link.setCourseId("course-1"); link.setFileId("file-1");
        com.match.course.persistence.CourseResourceRecord projection = new com.match.course.persistence.CourseResourceRecord();
        projection.setResourceId("file-1"); projection.setCourseId("course-1"); projection.setName("新名称"); projection.setResourceType("PPT");
        when(courseMapper.selectForUpdate("course-1")).thenReturn(course);
        when(chapterMapper.selectById("chapter-1")).thenReturn(chapter);
        when(linkMapper.selectLink("course-1", "file-1")).thenReturn(link);
        when(catalogMapper.selectByCourseAndFile("course-1", "file-1")).thenReturn(projection);
        CourseResourceMetadataRequest request = new CourseResourceMetadataRequest();
        request.setName("新名称"); request.setResourceType("ppt"); request.setChapterId("chapter-1"); request.setPracticeTool("editor"); request.setSortOrder(9);

        try {
            CourseAuthoringService.class
                    .getMethod("updateResourceMetadata", String.class, String.class, CourseResourceMetadataRequest.class)
                    .invoke(authoring, "course-1", "file-1", request);
        } catch (NoSuchMethodException error) {
            fail("课程资源缺少元数据更新接口"); return;
        }

        assertEquals("新名称", link.getDisplayName());
        assertEquals("PPT", link.getResourceType());
        assertEquals("chapter-1", link.getChapterId());
        assertEquals("EDITOR", link.getPracticeTool());
        assertEquals(Integer.valueOf(9), link.getSortOrder());
        verify(linkMapper).updateById(link);
    }

    @Test
    public void deletingCourseAlsoRemovesItsChapters() {
        CourseResourceCatalogMapper catalogMapper = mock(CourseResourceCatalogMapper.class);
        CourseResourceLinkMapper linkMapper = mock(CourseResourceLinkMapper.class);
        CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
        CourseAuthoringService authoring = new CourseAuthoringService(courseMapper, resourceMapper, catalogMapper, linkMapper);
        authoring.setChapterMapper(chapterMapper);
        CourseRecord course = new CourseRecord(); course.setCourseId("course-1");
        when(courseMapper.selectForUpdate("course-1")).thenReturn(course);

        authoring.delete("course-1");

        verify(chapterMapper).delete(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class));
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
