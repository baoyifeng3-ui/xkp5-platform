package com.match.controller;

import com.match.dto.AdminSubjectRequest;
import com.match.security.AdminGuard;
import com.match.service.impl.SubjectManagementService;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/subjects")
public class AdminSubjectController {
    private final SubjectManagementService subjectService;
    private final AdminGuard adminGuard;

    public AdminSubjectController(SubjectManagementService subjectService, AdminGuard adminGuard) {
        this.subjectService = subjectService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public ResponseResult<Object> list(@RequestParam String paperType,
                                       @RequestParam(required = false) String subjectType,
                                       @RequestParam(required = false) String keyword) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(subjectService.list(paperType, subjectType, keyword));
    }

    @PostMapping
    public ResponseResult<Object> create(@RequestBody AdminSubjectRequest request) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(subjectService.create(request));
    }

    @PutMapping("{subjectId}")
    public ResponseResult<Object> update(@PathVariable Integer subjectId,
                                         @RequestBody AdminSubjectRequest request) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(subjectService.update(subjectId, request));
    }

    @DeleteMapping("{subjectId}")
    public ResponseResult<Object> delete(@PathVariable Integer subjectId) {
        adminGuard.requireAdmin();
        subjectService.delete(subjectId);
        return Response.makeOKRsp("题目已删除");
    }

    @DeleteMapping("answers")
    public ResponseResult<Object> clearAnswers() {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(subjectService.clearAnswers());
    }

    @DeleteMapping
    public ResponseResult<Object> clearAll() {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(subjectService.clearAll());
    }
}
