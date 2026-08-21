package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.model.BindCompetitionSlotRequest;
import com.match.environment.service.CompetitionSlotBindingService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/competition-slots")
public class AdminCompetitionSlotController {
    private final RoleGuard roleGuard;
    private final CompetitionSlotBindingService service;

    public AdminCompetitionSlotController(RoleGuard roleGuard,
                                          CompetitionSlotBindingService service) {
        this.roleGuard = roleGuard;
        this.service = service;
    }

    @GetMapping
    public ResponseResult<Object> list() {
        User actor = roleGuard.requireBusinessAdmin();
        return Response.makeOKRsp(service.list(actor));
    }

    @PostMapping("/{slotId}/binding")
    public ResponseResult<Object> bind(@PathVariable String slotId,
                                       @RequestBody BindCompetitionSlotRequest request) {
        User actor = roleGuard.requireBusinessAdmin();
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("参赛用户编号不能为空");
        }
        return Response.makeOKRsp(service.bind(slotId, request.getUserId(), actor));
    }

    @DeleteMapping("/{slotId}/binding/{userId}")
    public ResponseResult<Object> unbind(@PathVariable String slotId,
                                         @PathVariable int userId) {
        User actor = roleGuard.requireBusinessAdmin();
        return Response.makeOKRsp(service.unbind(slotId, userId, actor));
    }
}
