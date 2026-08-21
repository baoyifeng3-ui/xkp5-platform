package com.match.registry.web;

import com.match.entity.User;
import com.match.registry.service.ImageCatalogQueryService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImageCatalogController {
    private final RoleGuard roleGuard;
    private final ImageCatalogQueryService catalog;
    public ImageCatalogController(RoleGuard roleGuard, ImageCatalogQueryService catalog) {
        this.roleGuard = roleGuard; this.catalog = catalog;
    }
    @GetMapping("/admin/image-groups")
    public ResponseResult<Object> groups(@RequestParam(defaultValue = "50") Integer limit) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(catalog.groups(roleGuard.roleOf(actor).name(), limit));
    }
    @GetMapping("/admin/image-artifacts")
    public ResponseResult<Object> artifacts(@RequestParam(defaultValue = "50") Integer limit) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(catalog.artifacts(roleGuard.roleOf(actor).name(), limit));
    }
}
