package com.match.course.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.course.model.CourseResourceRequest;
import com.match.course.model.CourseUpsertRequest;
import com.match.course.model.CourseView;
import com.match.course.model.ResourceView;
import com.match.course.persistence.CourseMapper;
import com.match.course.persistence.CourseRecord;
import com.match.course.persistence.CourseResourceMapper;
import com.match.course.persistence.CourseResourceRecord;
import com.match.resource.persistence.CourseResourceCatalogMapper;
import com.match.resource.persistence.CourseResourceLinkMapper;
import com.match.course.persistence.CourseChapterMapper;
import com.match.course.persistence.CourseChapterRecord;
import com.match.course.model.CourseChapterRequest;
import com.match.course.model.CourseResourceBindingRequest;
import com.match.course.model.CourseResourceMetadataRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CourseAuthoringService {
    private static final List<String> RESOURCE_TYPES =
            Collections.unmodifiableList(Arrays.asList("EBOOK", "VIDEO", "PPT", "ARCHIVE"));
    private static final Safelist COURSE_HTML = Safelist.relaxed()
            .addTags("table", "thead", "tbody", "tr", "th", "td", "font")
            .addAttributes("font", "color", "size", "face")
            .addAttributes("img", "alt", "title")
            .preserveRelativeLinks(true);

    private final CourseMapper courseMapper;
    private final CourseResourceMapper resourceMapper;
    private final CourseResourceCatalogMapper catalogMapper;
    private final CourseResourceLinkMapper linkMapper;
    private CourseChapterMapper chapterMapper;

    public CourseAuthoringService(CourseMapper courseMapper, CourseResourceMapper resourceMapper) {
        this(courseMapper, resourceMapper, null, null);
    }

    @Autowired
    public CourseAuthoringService(CourseMapper courseMapper, CourseResourceMapper resourceMapper,
                                  CourseResourceCatalogMapper catalogMapper,
                                  CourseResourceLinkMapper linkMapper) {
        this.courseMapper = courseMapper;
        this.resourceMapper = resourceMapper;
        this.catalogMapper = catalogMapper;
        this.linkMapper = linkMapper;
    }

    @Autowired public void setChapterMapper(CourseChapterMapper chapterMapper) { this.chapterMapper = chapterMapper; }

    public List<CourseChapterRecord> chapters(String courseId) { requireCourse(courseId); return chapterMapper.selectByCourse(courseId); }

    @Transactional public CourseChapterRecord createChapter(String courseId, CourseChapterRequest request, Integer actorId) {
        requireCourse(courseId); String name=request==null?null:request.getChapterName(); requireText(name,"章节名称不能为空");
        LocalDateTime now=LocalDateTime.now(); CourseChapterRecord record=new CourseChapterRecord(); record.setChapterId(UUID.randomUUID().toString()); record.setCourseId(courseId); record.setChapterName(name.trim()); record.setPracticeTool(normalizePracticeTool(request.getPracticeTool())); record.setSortOrder(request.getSortOrder()==null?0:request.getSortOrder()); record.setCreatedBy(actorId); record.setCreatedAt(now); record.setUpdatedAt(now); chapterMapper.insert(record); return record;
    }

    @Transactional public CourseChapterRecord updateChapter(String courseId, String chapterId, CourseChapterRequest request) {
        requireCourse(courseId);
        CourseChapterRecord chapter=chapterMapper.selectById(chapterId);
        if(chapter==null||!courseId.equals(chapter.getCourseId()))throw new IllegalArgumentException("章节不存在");
        requireText(request==null?null:request.getChapterName(),"章节名称不能为空");
        chapter.setChapterName(request.getChapterName().trim());
        chapter.setPracticeTool(normalizePracticeTool(request.getPracticeTool()));
        chapter.setSortOrder(request.getSortOrder()==null?0:request.getSortOrder());
        chapter.setUpdatedAt(LocalDateTime.now());
        chapterMapper.updateById(chapter);
        return chapter;
    }

    @Transactional public void deleteChapter(String courseId,String chapterId) {
        CourseChapterRecord chapter=chapterMapper.selectById(chapterId); if(chapter==null||!courseId.equals(chapter.getCourseId()))throw new IllegalArgumentException("章节不存在");
        if(linkMapper!=null)for(com.match.resource.persistence.CourseResourceLinkRecord link:linkMapper.selectByCourse(courseId)){if(chapterId.equals(link.getChapterId()))throw new IllegalArgumentException("章节仍包含课程资源，请先移动或删除资源");}
        chapterMapper.deleteById(chapterId);
    }

    @Transactional public ResourceView bindResource(String courseId,String fileId,CourseResourceBindingRequest request) {
        requireCourse(courseId); com.match.resource.persistence.CourseResourceLinkRecord link=linkMapper.selectLink(courseId,fileId); if(link==null)throw new IllegalArgumentException("课程资源关联不存在");
        String chapterId=request==null?null:request.getChapterId(); if(chapterId!=null&&!chapterId.trim().isEmpty()){CourseChapterRecord chapter=chapterMapper.selectById(chapterId);if(chapter==null||!courseId.equals(chapter.getCourseId()))throw new IllegalArgumentException("章节不存在");link.setChapterId(chapterId);}else link.setChapterId(null);
        link.setPracticeTool(normalizePracticeTool(request==null?null:request.getPracticeTool())); link.setUpdatedAt(LocalDateTime.now()); linkMapper.updateById(link); return resourceView(catalogMapper.selectByFileId(fileId));
    }

    @Transactional public ResourceView updateResourceMetadata(String courseId, String fileId, CourseResourceMetadataRequest request) {
        requireCourse(courseId);
        if(request==null)throw new IllegalArgumentException("资源信息不能为空");
        requireText(request.getName(),"资源名称不能为空");
        com.match.resource.persistence.CourseResourceLinkRecord link=linkMapper.selectLink(courseId,fileId);
        if(link==null)throw new IllegalArgumentException("课程资源关联不存在");
        String chapterId=request.getChapterId();
        if(chapterId!=null&&!chapterId.trim().isEmpty()){
            CourseChapterRecord chapter=chapterMapper.selectById(chapterId);
            if(chapter==null||!courseId.equals(chapter.getCourseId()))throw new IllegalArgumentException("章节不存在");
            link.setChapterId(chapterId);
        }else link.setChapterId(null);
        link.setDisplayName(request.getName().trim());
        link.setResourceType(normalizeType(request.getResourceType()));
        link.setPracticeTool(normalizePracticeTool(request.getPracticeTool()));
        link.setSortOrder(request.getSortOrder()==null?0:request.getSortOrder());
        link.setUpdatedAt(LocalDateTime.now());
        linkMapper.updateById(link);
        return resourceView(catalogMapper.selectByCourseAndFile(courseId,fileId));
    }

    private String normalizePracticeTool(String value){if(value==null||value.trim().isEmpty())return null;String normalized=value.trim().toUpperCase(Locale.ROOT);if(!Arrays.asList("ANNOTATION","EDITOR").contains(normalized))throw new IllegalArgumentException("实训工具类型无效");return normalized;}

    public List<CourseView> list() {
        QueryWrapper<CourseRecord> query = new QueryWrapper<>();
        query.orderByDesc("updated_at");
        return courseMapper.selectList(query).stream().map(this::view).collect(Collectors.toList());
    }

    @Transactional
    public CourseView create(CourseUpsertRequest request, Integer actorId) {
        validateCourse(request);
        LocalDateTime now = LocalDateTime.now();
        CourseRecord record = new CourseRecord();
        record.setCourseId(UUID.randomUUID().toString());
        apply(record, request);
        record.setEnabled(false);
        record.setCreatedBy(actorId);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        courseMapper.insert(record);
        return view(record);
    }

    @Transactional
    public CourseView update(String courseId, CourseUpsertRequest request) {
        validateCourse(request);
        CourseRecord record = requireCourse(courseId);
        apply(record, request);
        record.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(record);
        return view(record);
    }

    @Transactional
    public CourseView setEnabled(String courseId, boolean enabled) {
        CourseRecord record = requireCourse(courseId);
        record.setEnabled(enabled);
        record.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(record);
        return view(record);
    }

    @Transactional
    public void delete(String courseId) {
        CourseRecord record = requireCourse(courseId);
        if (linkMapper != null) linkMapper.delete(new QueryWrapper<com.match.resource.persistence.CourseResourceLinkRecord>().eq("course_id", courseId));
        if (chapterMapper != null) chapterMapper.delete(new QueryWrapper<CourseChapterRecord>().eq("course_id", courseId));
        resourceMapper.delete(new QueryWrapper<CourseResourceRecord>().eq("course_id", courseId));
        courseMapper.deleteById(record.getCourseId());
    }

    @Transactional
    public ResourceView addResource(String courseId, CourseResourceRequest request, Integer actorId) {
        requireCourse(courseId);
        String type = normalizeType(request.getResourceType());
        requireText(request.getName(), "资源名称不能为空");
        requireText(request.getStorageKey(), "资源存储标识不能为空");
        requireText(request.getSha256(), "资源摘要不能为空");
        if (!request.getSha256().trim().matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("资源摘要格式无效");
        }
        if (resourceMapper.selectByDigest(courseId, request.getSha256()) != null) {
            throw new IllegalArgumentException("相同资源已添加到该课程");
        }
        LocalDateTime now = LocalDateTime.now();
        CourseResourceRecord record = new CourseResourceRecord();
        record.setResourceId(UUID.randomUUID().toString());
        record.setCourseId(courseId);
        record.setResourceType(type);
        record.setName(request.getName().trim());
        record.setStorageKey(request.getStorageKey().trim());
        record.setContentLength(request.getContentLength());
        record.setSha256(request.getSha256().trim().toLowerCase(Locale.ROOT));
        record.setMimeType(request.getMimeType());
        record.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        record.setEnabled(true);
        record.setCreatedBy(actorId);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        resourceMapper.insert(record);
        return resourceView(record);
    }

    @Transactional
    public ResourceView setResourceEnabled(String resourceId, boolean enabled) {
        CourseResourceRecord record = resourceMapper.selectById(resourceId);
        if (record == null) {
            throw new IllegalArgumentException("课程资源不存在");
        }
        record.setEnabled(enabled);
        record.setUpdatedAt(LocalDateTime.now());
        resourceMapper.updateById(record);
        return resourceView(record);
    }

    private CourseView view(CourseRecord record) {
        List<CourseResourceRecord> records = catalogMapper == null
                ? resourceMapper.selectByCourseId(record.getCourseId())
                : catalogMapper.selectByCourseId(record.getCourseId());
        List<ResourceView> resources = records.stream()
                .map(this::resourceView).collect(Collectors.toList());
        return new CourseView(record, resources);
    }

    private ResourceView resourceView(CourseResourceRecord record) {
        return new ResourceView(record.getResourceId(), record.getCourseId(), record.getResourceType(),
                record.getChapterId(), record.getPracticeTool(),
                record.getName(), record.getContentLength(), record.getMimeType(), record.getSortOrder(),
                record.getEnabled());
    }

    private CourseRecord requireCourse(String courseId) {
        CourseRecord record = courseMapper.selectForUpdate(courseId);
        if (record == null) {
            throw new IllegalArgumentException("课程不存在");
        }
        return record;
    }

    private void apply(CourseRecord record, CourseUpsertRequest request) {
        record.setName(request.getName().trim());
        record.setCourseType(request.getCourseType().trim());
        record.setDescription(request.getDescription());
        record.setIntroductionHtml(sanitizeHtml(request.getIntroductionHtml()));
        record.setOutlineHtml(sanitizeHtml(request.getOutlineHtml()));
        record.setCoverResourceId(request.getCoverResourceId());
    }

    private String sanitizeHtml(String html) {
        return Jsoup.clean(html == null ? "" : html, "http://localhost", COURSE_HTML,
                new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
    }


    private void validateCourse(CourseUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("课程信息不能为空");
        }
        requireText(request.getName(), "课程名称不能为空");
        requireText(request.getCourseType(), "课程类别不能为空");
    }

    private String normalizeType(String type) {
        requireText(type, "资源类型不能为空");
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!RESOURCE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的资源类型");
        }
        return normalized;
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}
