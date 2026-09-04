package com.match.resource.web;

import com.match.entity.User;
import com.match.resource.model.ResourceDirectoryRequest;
import com.match.resource.service.ResourceSpaceService;
import com.match.resource.service.ResourceSpaceType;
import com.match.resource.service.ResourceFileDeliveryService;
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
import com.match.security.AdminAccessException;

@RestController
@RequestMapping("/user/resource-spaces")
public class UserResourceSpaceController {
    private final RoleGuard roles; private final ResourceSpaceService resources;
    private final ResourceFileDeliveryService deliveries;
    public UserResourceSpaceController(RoleGuard roles, ResourceSpaceService resources,
                                       ResourceFileDeliveryService deliveries) {
        this.roles = roles; this.resources = resources; this.deliveries = deliveries;
    }
    @GetMapping("/{space}/entries")
    public ResponseResult<Object> entries(@PathVariable String space,
                                          @RequestParam(required = false) String parentId) {
        User actor = roles.requireUser(); ResourceSpaceType type = participantSpace(space);
        return Response.makeOKRsp(resources.entries(type, UserRole.USER, actor.getUserId(), parentId));
    }
    @PostMapping("/{space}/directories")
    public ResponseResult<Object> createDirectory(@PathVariable String space,
                                                   @RequestBody ResourceDirectoryRequest request) {
        User actor = roles.requireUser(); ResourceSpaceType type = participantSpace(space);
        return Response.makeOKRsp(resources.createDirectory(type, UserRole.USER, actor.getUserId(),
                request.getParentId(), request.getName()));
    }
    @PostMapping("/{space}/files")
    public ResponseResult<Object> upload(@PathVariable String space,
                                         @RequestPart("file") MultipartFile file,
                                         @RequestParam(required = false) String directoryId) {
        User actor = roles.requireUser(); ResourceSpaceType type = participantSpace(space);
        return Response.makeOKRsp(resources.upload(type, UserRole.USER, actor.getUserId(), directoryId,
                file, Collections.emptyList(), "ARCHIVE"));
    }
    @GetMapping("/{space}/files/{fileId}/download")
    public ResponseResult<Object> download(@PathVariable String space, @PathVariable String fileId) {
        User actor = roles.requireUser(); ResourceSpaceType type = participantSpace(space);
        return Response.makeOKRsp(resources.download(type, UserRole.USER, actor.getUserId(), fileId));
    }
    @DeleteMapping("/{space}/files/{fileId}")
    public ResponseResult<Object> deleteFile(@PathVariable String space, @PathVariable String fileId) {
        User actor = roles.requireUser(); ResourceSpaceType type = participantSpace(space);
        resources.deleteFile(type, UserRole.USER, actor.getUserId(), fileId); return Response.makeOKRsp();
    }
    @DeleteMapping("/{space}/directories/{directoryId}")
    public ResponseResult<Object> deleteDirectory(@PathVariable String space,
                                                   @PathVariable String directoryId) {
        User actor = roles.requireUser(); ResourceSpaceType type = participantSpace(space);
        resources.deleteDirectory(type, UserRole.USER, actor.getUserId(), directoryId);
        return Response.makeOKRsp();
    }
    @PostMapping("/public/files/{fileId}/deliver")
    public ResponseResult<Object> deliver(@PathVariable String fileId,
                                          @RequestParam String environmentId) {
        User actor = roles.requireUser();
        return Response.makeOKRsp(deliveries.deliverPublicFile(fileId, environmentId,
                actor.getUserId()));
    }
    @PostMapping("/{space}/files/{fileId}/deliver")
    public ResponseResult<Object> deliverAny(@PathVariable String space, @PathVariable String fileId,
                                             @RequestParam String environmentId) {
        User actor = roles.requireUser();
        ResourceSpaceType type = participantSpace(space);
        return Response.makeOKRsp(deliveries.deliverFile(fileId, environmentId, actor.getUserId(), type.name()));
    }
    private ResourceSpaceType participantSpace(String value) {
        ResourceSpaceType type = ResourceSpaceType.parse(value);
        if (type == ResourceSpaceType.COURSE) throw new AdminAccessException("普通用户不显示课程资源库");
        return type;
    }
}
