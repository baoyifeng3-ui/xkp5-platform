package com.match.controller;

import com.match.dto.CompetitionAnnouncementRequest;
import com.match.security.AdminGuard;
import com.match.service.impl.CompetitionAnnouncementService;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/competition-announcements")
public class AdminCompetitionAnnouncementController {
    private final CompetitionAnnouncementService service;
    private final AdminGuard guard;

    public AdminCompetitionAnnouncementController(CompetitionAnnouncementService service, AdminGuard guard) {
        this.service = service; this.guard = guard;
    }

    @GetMapping public ResponseResult<Object> list() { guard.requireAdmin(); return Response.makeOKRsp(service.list()); }
    @PostMapping public ResponseResult<Object> create(@RequestBody CompetitionAnnouncementRequest request) {
        guard.requireAdmin(); return Response.makeOKRsp(service.create(request == null ? null : request.getValues()));
    }
    @PutMapping("/{id}") public ResponseResult<Object> update(@PathVariable Integer id,
            @RequestBody CompetitionAnnouncementRequest request) {
        guard.requireAdmin(); return Response.makeOKRsp(service.update(id, request == null ? null : request.getValues()));
    }
    @DeleteMapping("/{id}") public ResponseResult<Object> delete(@PathVariable Integer id) {
        guard.requireAdmin(); service.delete(id); return Response.makeOKRsp(null);
    }
}
