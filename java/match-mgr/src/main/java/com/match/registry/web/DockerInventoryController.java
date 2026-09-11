package com.match.registry.web;

import com.match.registry.service.DockerInventoryService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/docker-inventory/{agentId}")
public class DockerInventoryController {
    private final RoleGuard roles;
    private final DockerInventoryService inventory;
    public DockerInventoryController(RoleGuard roles, DockerInventoryService inventory) { this.roles=roles; this.inventory=inventory; }
    @GetMapping("/images") public ResponseResult<Object> images(@PathVariable String agentId) {
        roles.requireSuperAdmin(); return Response.makeOKRsp(inventory.images(agentId));
    }
    @PostMapping("/inspect") public ResponseResult<Object> inspect(@PathVariable String agentId,@RequestBody Target request) {
        return Response.makeOKRsp(inventory.inspect(agentId,request.target,roles.requireSuperAdmin().getUserId()));
    }
    @GetMapping("/commands/{commandId}") public ResponseResult<Object> status(@PathVariable String agentId,@PathVariable String commandId) {
        return Response.makeOKRsp(inventory.status(agentId,commandId,roles.requireSuperAdmin().getUserId()));
    }
    @DeleteMapping("/images") public ResponseResult<Object> deleteImage(@PathVariable String agentId,@RequestBody Target request) {
        return Response.makeOKRsp(inventory.deleteImage(agentId,request.target,request.id,roles.requireSuperAdmin().getUserId()));
    }
    @DeleteMapping("/containers") public ResponseResult<Object> deleteContainer(@PathVariable String agentId,@RequestBody Target request) {
        return Response.makeOKRsp(inventory.deleteContainer(agentId,request.target,request.id,roles.requireSuperAdmin().getUserId()));
    }
    public static class Target { public String target; public String id; }
}
