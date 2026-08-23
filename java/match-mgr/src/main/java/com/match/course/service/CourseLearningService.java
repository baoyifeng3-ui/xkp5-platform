package com.match.course.service;

import com.match.course.model.CourseView;
import com.match.course.persistence.CourseMapper;
import com.match.course.persistence.CourseProgressMapper;
import com.match.course.persistence.CourseProgressRecord;
import com.match.course.persistence.CourseRecord;
import com.match.course.persistence.CourseResourceMapper;
import com.match.course.persistence.CourseResourceRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.match.util.dfs.FastDFSClient;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.List;

@Service
public class CourseLearningService {
    private final CourseMapper courseMapper;
    private final CourseResourceMapper resourceMapper;
    private final CourseProgressMapper progressMapper;

    public CourseLearningService(CourseMapper courseMapper, CourseResourceMapper resourceMapper,
                                 CourseProgressMapper progressMapper) {
        this.courseMapper = courseMapper;
        this.resourceMapper = resourceMapper;
        this.progressMapper = progressMapper;
    }

    public void recordProgress(Integer userId, String resourceId, String progressKind,
                               Integer progressValue, Boolean completed) {
        recordProgress(userId, resourceId, progressKind, progressValue, completed, null, null, null);
    }

    public List<CourseView> visibleCourses() {
        return courseMapper.selectVisible().stream().map(record -> new CourseView(record,
                resourceMapper.selectByCourseId(record.getCourseId()).stream()
                        .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                        .map(r -> new com.match.course.model.ResourceView(r.getResourceId(), r.getCourseId(),
                                r.getResourceType(), r.getName(), r.getContentLength(), r.getMimeType(),
                                r.getSortOrder(), r.getEnabled())).collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    @Transactional
    public void recordProgress(Integer userId, String resourceId, String progressKind,
                               Integer progressValue, Boolean completed, Long currentValue,
                               Long totalValue, String lastPosition) {
        CourseResourceRecord resource = resourceMapper.selectById(resourceId);
        if (resource == null || !Boolean.TRUE.equals(resource.getEnabled())) {
            throw new IllegalArgumentException("课程资源不存在或已停用");
        }
        if (!java.util.Arrays.asList("PAGE", "TIME", "SLIDE", "DELIVERY").contains(progressKind)) {
            throw new IllegalArgumentException("学习进度类型无效");
        }
        CourseProgressRecord record = new CourseProgressRecord();
        long current = currentValue == null ? (progressValue == null ? 0 : progressValue) : currentValue;
        long total = totalValue == null || totalValue <= 0 ? 100 : totalValue;
        int value = (int) Math.max(0, Math.min(100, Math.round(current * 100.0 / total)));
        boolean done = Boolean.TRUE.equals(completed) || value >= 100;
        record.setProgressId(UUID.randomUUID().toString());
        record.setUserId(userId);
        record.setResourceId(resourceId);
        record.setProgressKind(progressKind);
        record.setCurrentValue(Math.max(0, current));
        record.setTotalValue(total);
        record.setPercent(BigDecimal.valueOf(value));
        record.setLastPosition(lastPosition);
        record.setState(done ? "COMPLETED" : "IN_PROGRESS");
        record.setCompletedAt(done ? LocalDateTime.now() : null);
        record.setUpdatedAt(LocalDateTime.now());
        progressMapper.upsertProgress(record);
    }

    public List<CourseProgressRecord> progress(Integer userId) {
        return progressMapper.selectByUser(userId);
    }

    public Map<String, Object> previewUrl(String resourceId) {
        CourseResourceRecord resource = resourceMapper.selectById(resourceId);
        if (resource == null || !Boolean.TRUE.equals(resource.getEnabled())) throw new IllegalArgumentException("课程资源不存在或已停用");
        CourseRecord course = courseMapper.selectById(resource.getCourseId());
        if (course == null || !Boolean.TRUE.equals(course.getEnabled())) throw new IllegalArgumentException("课程未发布");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", resourceId); result.put("resourceType", resource.getResourceType());
        result.put("previewUrl", FastDFSClient.getServerAccessUrl(resource.getStorageKey()));
        return result;
    }
}
