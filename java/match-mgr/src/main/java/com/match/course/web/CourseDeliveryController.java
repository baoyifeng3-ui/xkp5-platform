package com.match.course.web;

import com.match.course.service.CourseDeliveryService;
import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/course-resources")
public class CourseDeliveryController {
    private final RoleGuard roleGuard;
    private final CourseDeliveryService service;

    public CourseDeliveryController(RoleGuard roleGuard, CourseDeliveryService service) {
        this.roleGuard = roleGuard;
        this.service = service;
    }

    @PostMapping("/{resourceId}/deliver")
    public ResponseResult<Object> deliver(@PathVariable String resourceId, @RequestParam String environmentId) {
        User actor = roleGuard.requireUser();
        return Response.makeOKRsp(service.deliver(resourceId, environmentId, actor.getUserId(), actor.getUserId(), "USER"));
    }

    @org.springframework.web.bind.annotation.GetMapping("/deliveries")
    public ResponseResult<Object> deliveries() {
        User actor = roleGuard.requireUser();
        return Response.makeOKRsp(service.deliveriesForUser(actor.getUserId()));
    }

    @org.springframework.web.bind.annotation.GetMapping("/admin-deliveries")
    public ResponseResult<Object> adminDeliveries() {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(service.recentDeliveries());
    }

    @PostMapping("/{resourceId}/deliver-for-user")
    public ResponseResult<Object> deliverForUser(@PathVariable String resourceId,
                                                 @RequestParam String environmentId,
                                                 @RequestParam Integer userId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(service.deliver(resourceId, environmentId, userId, actor.getUserId(),
                roleGuard.roleOf(actor).name()));
    }

    @PostMapping("/{resourceId}/deliver-to-all")
    public ResponseResult<Object> deliverToAll(@PathVariable String resourceId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(service.deliverToAllUsers(resourceId, actor.getUserId(),
                roleGuard.roleOf(actor).name()));
    }
}
