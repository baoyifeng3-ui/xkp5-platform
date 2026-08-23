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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final CourseMapper courseMapper;
    private final CourseResourceMapper resourceMapper;

    public CourseAuthoringService(CourseMapper courseMapper, CourseResourceMapper resourceMapper) {
        this.courseMapper = courseMapper;
        this.resourceMapper = resourceMapper;
    }

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
        List<ResourceView> resources = resourceMapper.selectByCourseId(record.getCourseId()).stream()
                .map(this::resourceView).collect(Collectors.toList());
        return new CourseView(record, resources);
    }

    private ResourceView resourceView(CourseResourceRecord record) {
        return new ResourceView(record.getResourceId(), record.getCourseId(), record.getResourceType(),
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
        record.setCoverResourceId(request.getCoverResourceId());
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
