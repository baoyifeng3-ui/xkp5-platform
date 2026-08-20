package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.model.ContainerTemplateRequest;
import com.match.environment.service.ContainerTemplateService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/super-admin/container-templates")
public class SuperAdminContainerTemplateController {
    private final RoleGuard roleGuard;
    private final ContainerTemplateService templateService;

    public SuperAdminContainerTemplateController(RoleGuard roleGuard,
                                                 ContainerTemplateService templateService) {
        this.roleGuard = roleGuard;
        this.templateService = templateService;
    }

    @PostMapping
    public ResponseResult<Object> publish(@RequestBody ContainerTemplateRequest request) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(templateService.publish(request, actor.getUserId()));
    }

    @PostMapping("/{templateId}/versions/{version}/disable")
    public ResponseResult<Object> disable(@PathVariable String templateId, @PathVariable int version) {
        User actor = roleGuard.requireSuperAdmin();
        templateService.disable(templateId, version, actor.getUserId());
        return Response.makeOKRsp(null);
    }

    @GetMapping
    public ResponseResult<Object> list() {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(templateService.list());
    }

    @GetMapping("/{templateId}/versions")
    public ResponseResult<Object> versions(@PathVariable String templateId) {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(templateService.versions(templateId));
    }
}
