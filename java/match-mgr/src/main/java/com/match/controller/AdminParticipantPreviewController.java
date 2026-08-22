package com.match.controller;

import com.match.entity.Subject;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.environment.service.EnvironmentOperationService;
import com.match.security.AdminGuard;
import com.match.service.impl.SubjectManagementService;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/participant-preview")
public class AdminParticipantPreviewController {
    private final AdminGuard adminGuard;
    private final SubjectManagementService subjectService;
    private final EnvironmentOperationService environmentService;

    public AdminParticipantPreviewController(AdminGuard adminGuard,
                                             SubjectManagementService subjectService,
                                             EnvironmentOperationService environmentService) {
        this.adminGuard = adminGuard;
        this.subjectService = subjectService;
        this.environmentService = environmentService;
    }

    @GetMapping("/subjects")
    public ResponseResult<Object> subjects(@RequestParam String testPaperType) {
        adminGuard.requireAdmin();
        List<Map<String, Object>> records = new ArrayList<>();
        for (Subject subject : subjectService.list(testPaperType, null, null)) {
            Map<String, Object> safeSubject = new LinkedHashMap<>();
            safeSubject.put("modular", subject.getModular());
            safeSubject.put("modularName", subject.getModularName());
            safeSubject.put("subjectType", subject.getSubjectType());
            safeSubject.put("subjectName", subject.getSubjectName());
            safeSubject.put("options", subject.getOptions());
            safeSubject.put("screenshotRequirement", subject.getScreenshotRequirement());
            safeSubject.put("point", subject.getPoint());
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("subject", safeSubject);
            record.put("answerSheet", null);
            records.add(record);
        }
        return Response.makeOKRsp(records);
    }

    @GetMapping("/training-environments")
    public ResponseResult<Object> trainingEnvironments() {
        adminGuard.requireAdmin();
        List<Map<String, Object>> records = new ArrayList<>();
        int index = 0;
        for (TrainingEnvironmentRecord environment : environmentService.listAll()) {
            Map<String, Object> safeEnvironment = new LinkedHashMap<>();
            safeEnvironment.put("courseLabel", "课程环境 " + (++index));
            safeEnvironment.put("actualState", environment.getActualState());
            records.add(safeEnvironment);
        }
        return Response.makeOKRsp(records);
    }

    @GetMapping("/competition-environment")
    public ResponseResult<Object> competitionEnvironment() {
        adminGuard.requireAdmin();
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("readiness", "UNBOUND");
        return Response.makeOKRsp(preview);
    }
}
