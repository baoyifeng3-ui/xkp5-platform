package com.match.help.web;

import com.match.entity.User;
import com.match.help.service.HelpDocumentService;
import com.match.resource.service.ResourceStorageService;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;

@RestController
public class HelpDocumentController {
    private final RoleGuard roles;
    private final HelpDocumentService service;
    private final ResourceStorageService storage;

    public HelpDocumentController(RoleGuard roles, HelpDocumentService service, ResourceStorageService storage) {
        this.roles = roles; this.service = service; this.storage = storage;
    }

    @GetMapping("/help-documents")
    public ResponseResult<Object> visible() {
        User user = roles.requireBusinessUser();
        return Response.makeOKRsp(service.visible(roles.roleOf(user) == UserRole.ADMIN));
    }

    @GetMapping("/super-admin/help-documents")
    public ResponseResult<Object> all() { roles.requireSuperAdmin(); return Response.makeOKRsp(service.all()); }

    @PostMapping("/super-admin/help-documents")
    public ResponseResult<Object> save(@RequestBody Map<String, Object> body) {
        User actor = roles.requireSuperAdmin(); return Response.makeOKRsp(service.save(body, actor.getUserId()));
    }

    @PostMapping("/super-admin/help-documents/{id}/published")
    public ResponseResult<Object> publish(@PathVariable String id, @RequestParam boolean value) {
        roles.requireSuperAdmin(); return Response.makeOKRsp(service.publish(id, value));
    }

    @PostMapping("/super-admin/help-documents/upload")
    public ResponseResult<Object> upload(@RequestParam String title, @RequestParam String audience,
                                         @RequestPart MultipartFile file) {
        User actor = roles.requireSuperAdmin();
        return Response.makeOKRsp(service.upload(title, audience, file, actor.getUserId()));
    }

    @PostMapping("/super-admin/help-images")
    public ResponseResult<Object> image(@RequestPart MultipartFile file) {
        roles.requireSuperAdmin(); ResourceStorageService.StoredFile stored = storage.store(file);
        return Response.makeOKRsp(Collections.singletonMap("url", "/files/" + stored.getStorageKey()));
    }
}
