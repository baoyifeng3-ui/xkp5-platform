package com.match.controller;

import com.match.dto.TrainingAssignmentRequest;
import com.match.entity.TrainingServer;
import com.match.security.AdminGuard;
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
    private final AdminGuard adminGuard;

    public AdminTrainingController(TrainingEnvironmentService trainingService, AdminGuard adminGuard) {
        this.trainingService = trainingService;
        this.adminGuard = adminGuard;
    }

    @GetMapping("servers")
    public ResponseResult<Object> servers() {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(trainingService.listServers());
    }

    @GetMapping("servers/heartbeat")
    public ResponseResult<Object> serverHeartbeat() {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(trainingService.heartbeatServers());
    }

    @PostMapping("servers")
    public ResponseResult<Object> createServer(@RequestBody TrainingServer server) {
        adminGuard.requireAdmin();
        server.setTrainingServerId(null);
        return Response.makeOKRsp(trainingService.saveServer(server));
    }

    @PutMapping("servers")
    public ResponseResult<Object> updateServer(@RequestBody TrainingServer server) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(trainingService.saveServer(server));
    }

    @DeleteMapping("servers/{trainingServerId}")
    public ResponseResult<Object> deleteServer(@PathVariable Integer trainingServerId) {
        adminGuard.requireAdmin();
        trainingService.deleteServer(trainingServerId);
        return Response.makeOKRsp("服务器已删除");
    }

    @PostMapping("assignments")
    public ResponseResult<Object> assign(@RequestBody TrainingAssignmentRequest request) {
        adminGuard.requireAdmin();
        trainingService.assign(request.getUserId(), request.getTrainingNodeId());
        return Response.makeOKRsp("三端口分配成功");
    }

    @DeleteMapping("assignments")
    public ResponseResult<Object> unassign(@RequestParam Integer userId) {
        adminGuard.requireAdmin();
        trainingService.unassign(userId);
        return Response.makeOKRsp("已取消分配");
    }
}
