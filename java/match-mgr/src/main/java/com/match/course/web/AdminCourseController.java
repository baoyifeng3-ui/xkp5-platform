package com.match.course.web;

import com.match.course.model.CourseResourceRequest;
import com.match.course.model.CourseUpsertRequest;
import com.match.course.service.CourseAuthoringService;
import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/courses")
public class AdminCourseController {
    private final RoleGuard roleGuard;
    private final CourseAuthoringService authoringService;

    public AdminCourseController(RoleGuard roleGuard, CourseAuthoringService authoringService) {
        this.roleGuard = roleGuard;
        this.authoringService = authoringService;
    }

    @GetMapping
    public ResponseResult<Object> list() {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.list());
    }

    @PostMapping
    public ResponseResult<Object> create(@RequestBody CourseUpsertRequest request) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.create(request, actor.getUserId()));
    }

    @PutMapping("/{courseId}")
    public ResponseResult<Object> update(@PathVariable String courseId,
                                         @RequestBody CourseUpsertRequest request) {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.update(courseId, request));
    }

    @PostMapping("/{courseId}/enabled")
    public ResponseResult<Object> setEnabled(@PathVariable String courseId,
                                             @RequestParam boolean enabled) {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.setEnabled(courseId, enabled));
    }

    @PostMapping("/{courseId}/resources")
    public ResponseResult<Object> addResource(@PathVariable String courseId,
                                              @RequestBody CourseResourceRequest request) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.addResource(courseId, request, actor.getUserId()));
    }

    @PostMapping("/resources/{resourceId}/enabled")
    public ResponseResult<Object> setResourceEnabled(@PathVariable String resourceId,
                                                     @RequestParam boolean enabled) {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.setResourceEnabled(resourceId, enabled));
    }
}
