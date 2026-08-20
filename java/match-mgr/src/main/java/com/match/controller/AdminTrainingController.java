package com.match.controller;

import com.match.dto.TrainingAssignmentRequest;
import com.match.entity.TrainingServer;
import com.match.security.RoleGuard;
import com.match.service.impl.TrainingEnvironmentService;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/training")
public class AdminTrainingController {
    private final TrainingEnvironmentService trainingService;
    private final RoleGuard roleGuard;

    public AdminTrainingController(TrainingEnvironmentService trainingService, RoleGuard roleGuard) {
        this.trainingService = trainingService;
        this.roleGuard = roleGuard;
    }

    @GetMapping("servers")
    public ResponseResult<Object> servers() {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(trainingService.listServers());
    }

    @GetMapping("servers/heartbeat")
    public ResponseResult<Object> serverHeartbeat() {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(trainingService.heartbeatServers());
    }

    @PostMapping("servers")
    public ResponseResult<Object> createServer(@RequestBody TrainingServer server) {
        roleGuard.requireSuperAdmin();
        server.setTrainingServerId(null);
        return Response.makeOKRsp(trainingService.saveServer(server));
    }

    @PutMapping("servers")
    public ResponseResult<Object> updateServer(@RequestBody TrainingServer server) {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(trainingService.saveServer(server));
    }

    @DeleteMapping("servers/{trainingServerId}")
    public ResponseResult<Object> deleteServer(@PathVariable Integer trainingServerId) {
        roleGuard.requireSuperAdmin();
        trainingService.deleteServer(trainingServerId);
        return Response.makeOKRsp("服务器已删除");
    }

    @PostMapping("assignments")
    public ResponseResult<Object> assign(@RequestBody TrainingAssignmentRequest request) {
        roleGuard.requireSuperAdmin();
        trainingService.assign(request.getUserId(), request.getTrainingNodeId());
        return Response.makeOKRsp("三端口分配成功");
    }

    @DeleteMapping("assignments")
    public ResponseResult<Object> unassign(@RequestParam Integer userId) {
        roleGuard.requireSuperAdmin();
        trainingService.unassign(userId);
        return Response.makeOKRsp("已取消分配");
    }
}
