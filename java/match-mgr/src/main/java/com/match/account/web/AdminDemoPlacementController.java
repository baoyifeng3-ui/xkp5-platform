package com.match.account.web;

import com.match.account.service.AccountSlotService;
import com.match.entity.User;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/demo-placement")
public class AdminDemoPlacementController {
    private final RoleGuard roles;
    private final AccountSlotService service;
    private final ProcessingEnvironmentSlotMapper slots;

    public AdminDemoPlacementController(RoleGuard roles, AccountSlotService service,
                                        ProcessingEnvironmentSlotMapper slots) {
        this.roles = roles;
        this.service = service;
        this.slots = slots;
    }

    @GetMapping
    public ResponseResult<Object> get() {
        User admin = roles.requireBusinessAdmin();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", slots.selectByUser(admin.getUserId()));
        result.put("slots", slots.selectAll());
        return Response.makeOKRsp(result);
    }

    @PostMapping
    public ResponseResult<Object> bind(@RequestBody Map<String, String> request) {
        User admin = roles.requireBusinessAdmin();
        String slotId = request == null ? null : request.get("slotId");
        if (slotId == null || slotId.trim().isEmpty()) throw new IllegalArgumentException("请选择教学槽位");
        return Response.makeOKRsp(service.bind(admin.getUserId(), slotId, admin.getUserId()));
    }
}
