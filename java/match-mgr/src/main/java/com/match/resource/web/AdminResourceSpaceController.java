package com.match.resource.web;

import com.match.entity.User;
import com.match.resource.model.CourseResourceLinkRequest;
import com.match.resource.model.ResourceDirectoryRequest;
import com.match.resource.service.ResourceSpaceService;
import com.match.resource.service.ResourceSpaceType;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import com.match.security.AdminAccessException;

@RestController
@RequestMapping("/admin/resource-spaces")
public class AdminResourceSpaceController {
    private final RoleGuard roles;
    private final ResourceSpaceService resources;
    public AdminResourceSpaceController(RoleGuard roles, ResourceSpaceService resources) {
        this.roles = roles; this.resources = resources;
    }

    @GetMapping("/{space}/entries")
    public ResponseResult<Object> entries(@PathVariable String space,
                                          @RequestParam(required = false) String parentId) {
        User actor = roles.requireBusinessAdmin();
        ResourceSpaceType type = ResourceSpaceType.parse(space);
        if (type == ResourceSpaceType.HOMEWORK) throw new AdminAccessException("管理员不能查看作业空间内容");
        return Response.makeOKRsp(resources.entries(type, UserRole.ADMIN, actor.getUserId(), parentId));
    }

    @PostMapping("/{space}/directories")
    public ResponseResult<Object> createDirectory(@PathVariable String space,
                                                   @RequestBody ResourceDirectoryRequest request) {
        User actor = roles.requireBusinessAdmin();
        return Response.makeOKRsp(resources.createDirectory(ResourceSpaceType.parse(space),
                UserRole.ADMIN, actor.getUserId(), request.getParentId(), request.getName()));
    }

    @PostMapping("/{space}/files")
    public ResponseResult<Object> upload(@PathVariable String space,
                                         @RequestPart("file") MultipartFile file,
                                         @RequestParam(required = false) String directoryId,
                                         @RequestParam(required = false) List<String> courseIds,
                                         @RequestParam(defaultValue = "ARCHIVE") String resourceType) {
        User actor = roles.requireBusinessAdmin();
        return Response.makeOKRsp(resources.upload(ResourceSpaceType.parse(space), UserRole.ADMIN,
                actor.getUserId(), directoryId, file,
                courseIds == null ? Collections.emptyList() : courseIds, resourceType));
    }

    @GetMapping("/{space}/files/{fileId}/download")
    public ResponseResult<Object> download(@PathVariable String space, @PathVariable String fileId) {
        User actor = roles.requireBusinessAdmin();
        return Response.makeOKRsp(resources.download(ResourceSpaceType.parse(space), UserRole.ADMIN,
                actor.getUserId(), fileId));
    }

    @DeleteMapping("/{space}/files/{fileId}")
    public ResponseResult<Object> deleteFile(@PathVariable String space, @PathVariable String fileId) {
        User actor = roles.requireBusinessAdmin();
        resources.deleteFile(ResourceSpaceType.parse(space), UserRole.ADMIN, actor.getUserId(), fileId);
        return Response.makeOKRsp();
    }

    @DeleteMapping("/{space}/directories/{directoryId}")
    public ResponseResult<Object> deleteDirectory(@PathVariable String space,
                                                   @PathVariable String directoryId) {
        User actor = roles.requireBusinessAdmin();
        resources.deleteDirectory(ResourceSpaceType.parse(space), UserRole.ADMIN,
                actor.getUserId(), directoryId); return Response.makeOKRsp();
    }

    @GetMapping("/homework/summary")
    public ResponseResult<Object> homeworkSummary() {
        roles.requireBusinessAdmin();
        return Response.makeOKRsp(resources.summary(ResourceSpaceType.HOMEWORK));
    }

    @PostMapping("/{space}/clear")
    public ResponseResult<Object> clear(@PathVariable String space,
                                        @RequestParam(defaultValue = "false") boolean confirm) {
        User actor = roles.requireBusinessAdmin();
        if (!confirm) throw new IllegalArgumentException("请确认一键清空操作");
        return Response.makeOKRsp(resources.clear(ResourceSpaceType.parse(space), actor.getUserId()));
    }

    @GetMapping("/course/files")
    public ResponseResult<Object> courseFiles() {
        roles.requireBusinessAdmin(); return Response.makeOKRsp(resources.courseFiles());
    }

    @PostMapping("/course/files/{fileId}/courses/{courseId}")
    public ResponseResult<Object> link(@PathVariable String fileId, @PathVariable String courseId,
                                       @RequestBody CourseResourceLinkRequest request) {
        User actor = roles.requireBusinessAdmin();
        return Response.makeOKRsp(resources.link(courseId, fileId, request.getResourceType(), actor.getUserId()));
    }

    @DeleteMapping("/course/files/{fileId}/courses/{courseId}")
    public ResponseResult<Object> unlink(@PathVariable String fileId, @PathVariable String courseId) {
        roles.requireBusinessAdmin(); resources.unlink(courseId, fileId); return Response.makeOKRsp();
    }
}
