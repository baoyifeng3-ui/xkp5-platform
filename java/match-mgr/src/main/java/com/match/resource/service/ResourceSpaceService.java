package com.match.resource.service;

import com.match.course.persistence.CourseMapper;
import com.match.course.persistence.CourseRecord;
import com.match.resource.persistence.CourseResourceLinkMapper;
import com.match.resource.persistence.CourseResourceLinkRecord;
import com.match.resource.persistence.PlatformFileMapper;
import com.match.resource.persistence.PlatformFileRecord;
import com.match.resource.persistence.ResourceCleanupAuditMapper;
import com.match.resource.persistence.ResourceCleanupAuditRecord;
import com.match.resource.persistence.ResourceDirectoryMapper;
import com.match.resource.persistence.ResourceDirectoryRecord;
import com.match.security.AdminAccessException;
import com.match.security.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ResourceSpaceService {
    private static final List<String> TYPES = Arrays.asList("EBOOK", "VIDEO", "PPT", "ARCHIVE");
    private final ResourceDirectoryMapper directories;
    private final PlatformFileMapper files;
    private final CourseResourceLinkMapper links;
    private final ResourceCleanupAuditMapper audits;
    private final CourseMapper courses;
    private final ResourceStorageService storage;
    private final ResourceAccessPolicy access;

    public ResourceSpaceService(ResourceDirectoryMapper directories, PlatformFileMapper files,
                                CourseResourceLinkMapper links, ResourceCleanupAuditMapper audits,
                                CourseMapper courses, ResourceStorageService storage,
                                ResourceAccessPolicy access) {
        this.directories = directories; this.files = files; this.links = links; this.audits = audits;
        this.courses = courses; this.storage = storage; this.access = access;
    }

    public Map<String, Object> entries(ResourceSpaceType space, UserRole role, Integer actorId,
                                       String parentId) {
        Integer ownerId = owner(space, actorId);
        require(access.canList(space, role, actorId, ownerId), "无权查看该资源空间");
        if (parentId != null && directories.selectScoped(parentId, space.name(), ownerId) == null) {
            throw new ResourceOperationException(404, "DIRECTORY_NOT_FOUND", "目录不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spaceType", space.name()); result.put("parentId", parentId);
        result.put("directories", directories.selectChildren(space.name(), ownerId, parentId));
        result.put("files", files.selectEntries(space.name(), ownerId, parentId));
        return result;
    }

    @Transactional
    public ResourceDirectoryRecord createDirectory(ResourceSpaceType space, UserRole role,
                                                   Integer actorId, String parentId, String name) {
        require(access.canUpload(space, role), "无权在该资源空间创建目录");
        String normalized = validName(name);
        Integer ownerId = owner(space, actorId);
        if (parentId != null && directories.selectScoped(parentId, space.name(), ownerId) == null) {
            throw new ResourceOperationException(404, "DIRECTORY_NOT_FOUND", "上级目录不存在");
        }
        for (ResourceDirectoryRecord item : directories.selectChildren(space.name(), ownerId, parentId)) {
            if (item.getName().equalsIgnoreCase(normalized)) throw new IllegalArgumentException("同名目录已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        ResourceDirectoryRecord record = new ResourceDirectoryRecord();
        record.setDirectoryId(UUID.randomUUID().toString()); record.setSpaceType(space.name());
        record.setOwnerUserId(ownerId); record.setParentId(parentId); record.setName(normalized);
        record.setCreatedBy(actorId); record.setCreatedAt(now); record.setUpdatedAt(now);
        directories.insert(record); return record;
    }

    @Transactional
    public PlatformFileRecord upload(ResourceSpaceType space, UserRole role, Integer actorId,
                                     String directoryId, MultipartFile upload, List<String> courseIds,
                                     String resourceType) {
        require(access.canUpload(space, role), "无权在该资源空间上传文件");
        List<String> selectedCourses = courseIds == null ? Collections.emptyList() : courseIds;
        String type = normalizeType(resourceType);
        if (space == ResourceSpaceType.COURSE && selectedCourses.isEmpty()) {
            throw new IllegalArgumentException("课程资源必须至少关联一门课程");
        }
        for (String courseId : selectedCourses) requireCourse(courseId);
        Integer ownerId = owner(space, actorId);
        if (directoryId != null && directories.selectScoped(directoryId, space.name(), ownerId) == null) {
            throw new ResourceOperationException(404, "DIRECTORY_NOT_FOUND", "上传目录不存在");
        }
        ResourceStorageService.StoredFile stored = storage.store(upload);
        LocalDateTime now = LocalDateTime.now();
        PlatformFileRecord record = new PlatformFileRecord();
        record.setFileId(UUID.randomUUID().toString()); record.setSpaceType(space.name());
        record.setOwnerUserId(ownerId); record.setDirectoryId(directoryId);
        record.setFileName(validName(upload.getOriginalFilename())); record.setStorageKey(stored.getStorageKey());
        record.setContentLength(stored.getContentLength()); record.setMimeType(stored.getMimeType());
        record.setSha256(stored.getSha256()); record.setUploadedBy(actorId);
        record.setCreatedAt(now); record.setUpdatedAt(now);
        try {
            files.insert(record);
            for (String courseId : selectedCourses) link(courseId, record.getFileId(), type, actorId);
            return record;
        } catch (RuntimeException error) {
            storage.delete(stored.getStorageKey());
            throw error;
        }
    }

    @Transactional
    public CourseResourceLinkRecord link(String courseId, String fileId, String resourceType,
                                         Integer actorId) {
        requireCourse(courseId);
        PlatformFileRecord file = files.selectById(fileId);
        if (file == null || !ResourceSpaceType.COURSE.name().equals(file.getSpaceType())) {
            throw new IllegalArgumentException("课程资源文件不存在");
        }
        if (links.selectLink(courseId, fileId) != null) throw new IllegalArgumentException("课程已关联该资源");
        LocalDateTime now = LocalDateTime.now();
        CourseResourceLinkRecord record = new CourseResourceLinkRecord();
        record.setLinkId(UUID.randomUUID().toString()); record.setCourseId(courseId); record.setFileId(fileId);
        record.setResourceType(normalizeType(resourceType)); record.setSortOrder(0); record.setEnabled(true);
        record.setCreatedBy(actorId); record.setCreatedAt(now); record.setUpdatedAt(now);
        links.insert(record); return record;
    }

    @Transactional
    public void unlink(String courseId, String fileId) {
        if (links.deleteLink(courseId, fileId) == 0) throw new IllegalArgumentException("课程资源关联不存在");
    }

    @Transactional
    public void deleteFile(ResourceSpaceType space, UserRole role, Integer actorId, String fileId) {
        Integer ownerId = owner(space, actorId);
        PlatformFileRecord file = files.selectScoped(fileId, space.name(), ownerId);
        if (file == null) throw new ResourceOperationException(404, "FILE_NOT_FOUND", "文件不存在");
        require(access.canDelete(space, role, actorId, space == ResourceSpaceType.EXCHANGE
                ? file.getUploadedBy() : file.getOwnerUserId()), "无权删除该文件");
        if (!links.selectByFile(fileId).isEmpty()) throw new ResourceOperationException(409, "COURSE_REFERENCE_EXISTS", "文件仍被课程引用，请先解除课程关联");
        if (!storage.delete(file.getStorageKey())) throw new ResourceOperationException(503, "STORAGE_DELETE_FAILED", "文件存储服务删除失败");
        files.deleteById(fileId);
    }

    @Transactional
    public void deleteDirectory(ResourceSpaceType space, UserRole role, Integer actorId, String directoryId) {
        Integer ownerId = owner(space, actorId);
        ResourceDirectoryRecord directory = directories.selectScoped(directoryId, space.name(), ownerId);
        if (directory == null) throw new ResourceOperationException(404, "DIRECTORY_NOT_FOUND", "目录不存在");
        require(access.canDelete(space, role, actorId, directory.getCreatedBy()), "无权删除该目录");
        if (directories.countChildDirectories(directoryId) > 0 || files.countInDirectory(directoryId) > 0) {
            throw new ResourceOperationException(409, "DIRECTORY_NOT_EMPTY", "目录不为空，不能删除");
        }
        directories.deleteById(directoryId);
    }

    public Map<String, Object> download(ResourceSpaceType space, UserRole role, Integer actorId,
                                        String fileId) {
        Integer ownerId = owner(space, actorId);
        PlatformFileRecord file = files.selectScoped(fileId, space.name(), ownerId);
        if (file == null) throw new ResourceOperationException(404, "FILE_NOT_FOUND", "文件不存在");
        require(access.canList(space, role, actorId, ownerId), "无权下载该文件");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileId", fileId); result.put("fileName", file.getFileName());
        result.put("downloadUrl", "/api/files/" + file.getStorageKey()); return result;
    }

    public Map<String, Object> summary(ResourceSpaceType space) {
        Map<String, Object> value = files.summarizeSpace(space.name());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileCount", number(value, "file_count"));
        result.put("totalBytes", number(value, "total_bytes")); return result;
    }

    @Transactional
    public Map<String, Object> clear(ResourceSpaceType space, Integer actorId) {
        if (space != ResourceSpaceType.EXCHANGE && space != ResourceSpaceType.HOMEWORK) {
            throw new IllegalArgumentException("该资源空间不支持一键清空");
        }
        Map<String, Object> totals = summary(space);
        List<PlatformFileRecord> existing = files.selectAllInSpace(space.name());
        for (PlatformFileRecord file : existing) {
            if (!storage.delete(file.getStorageKey())) throw new ResourceOperationException(503, "STORAGE_CLEAR_FAILED", "文件存储服务清理失败");
        }
        files.deleteBySpace(space.name()); directories.deleteBySpace(space.name());
        ResourceCleanupAuditRecord audit = new ResourceCleanupAuditRecord();
        audit.setAuditId(UUID.randomUUID().toString()); audit.setSpaceType(space.name());
        audit.setDeletedFileCount(((Number) totals.get("fileCount")).longValue());
        audit.setDeletedTotalBytes(((Number) totals.get("totalBytes")).longValue());
        audit.setActorUserId(actorId); audit.setCreatedAt(LocalDateTime.now()); audits.insert(audit);
        return totals;
    }

    public List<PlatformFileRecord> courseFiles() { return files.selectAllInSpace("COURSE"); }
    public List<CourseResourceLinkRecord> linksForFile(String fileId) { return links.selectByFile(fileId); }

    private Integer owner(ResourceSpaceType space, Integer actorId) {
        return space == ResourceSpaceType.HOMEWORK ? actorId : null;
    }
    private String normalizeType(String value) { String result = value == null ? "ARCHIVE" : value.trim().toUpperCase(Locale.ROOT); if (!TYPES.contains(result)) throw new IllegalArgumentException("资源类型无效"); return result; }
    private String validName(String value) { if (value == null) throw new IllegalArgumentException("名称不能为空"); String result = value.trim(); if (result.isEmpty() || ".".equals(result) || "..".equals(result) || result.contains("/") || result.contains("\\")) throw new IllegalArgumentException("名称无效"); return result; }
    private void requireCourse(String courseId) { CourseRecord course = courses.selectById(courseId); if (course == null) throw new IllegalArgumentException("课程不存在"); }
    private void require(boolean allowed, String message) { if (!allowed) throw new AdminAccessException(message); }
    private long number(Map<String, Object> value, String key) { if (value == null || value.get(key) == null) return 0; return ((Number) value.get(key)).longValue(); }
}
