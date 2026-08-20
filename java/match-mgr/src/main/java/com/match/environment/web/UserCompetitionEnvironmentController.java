package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.service.CompetitionSlotBindingService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/competition-environment")
public class UserCompetitionEnvironmentController {
    private final RoleGuard roleGuard;
    private final CompetitionSlotBindingService service;

    public UserCompetitionEnvironmentController(RoleGuard roleGuard,
                                                CompetitionSlotBindingService service) {
        this.roleGuard = roleGuard;
        this.service = service;
    }

    @GetMapping
    public ResponseResult<Object> current() {
        User participant = roleGuard.requireUser();
        return Response.makeOKRsp(service.currentForUser(participant));
    }
}
