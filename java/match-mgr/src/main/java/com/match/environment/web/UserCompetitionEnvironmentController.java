package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.service.CompetitionSlotBindingService;
import com.match.environment.service.EnvironmentOperationService;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/competition-environment")
public class UserCompetitionEnvironmentController {
    private final RoleGuard roleGuard;
    private final CompetitionSlotBindingService service;
    private final EnvironmentOperationService operations;
    private final EnvironmentPortAllocationMapper ports;
    private final ProcessingAgentMapper agents;

    public UserCompetitionEnvironmentController(RoleGuard roleGuard,
                                                CompetitionSlotBindingService service) {
        this(roleGuard, service, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UserCompetitionEnvironmentController(RoleGuard roleGuard,
                                                CompetitionSlotBindingService service,
                                                EnvironmentOperationService operations,
                                                EnvironmentPortAllocationMapper ports,
                                                ProcessingAgentMapper agents) {
        this.roleGuard = roleGuard;
        this.service = service;
        this.operations = operations;
        this.ports = ports;
        this.agents = agents;
    }

    @GetMapping
    public ResponseResult<Object> current() {
        User participant = roleGuard.requireUser();
        TrainingEnvironmentRecord environment = competitionEnvironmentOrNull(participant.getUserId());
        return Response.makeOKRsp(environment == null
                ? service.currentForUser(participant) : currentTrainingEnvironment(participant));
    }

    @PostMapping("/start")
    public ResponseResult<Object> start() {
        User participant = roleGuard.requireUser();
        TrainingEnvironmentRecord environment = competitionEnvironmentOrNull(participant.getUserId());
        if (environment == null) return Response.makeOKRsp(service.currentForUser(participant));
        operations.start(environment.getEnvironmentId(), participant.getUserId(), "USER");
        return Response.makeOKRsp(currentTrainingEnvironment(participant));
    }

    private java.util.Map<String, Object> currentTrainingEnvironment(User participant) {
        TrainingEnvironmentRecord environment = competitionEnvironment(participant.getUserId());
        java.util.Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("environmentId", environment.getEnvironmentId());
        view.put("readiness", environment.getActualState());
        view.put("readinessCode", environment.getActualState());
        view.put("agentId", environment.getAgentId());
        view.put("slotNumber", environment.getSlotNumber());
        ProcessingAgentRecord agent = agents.selectForManagement(environment.getAgentId());
        if (agent == null || agent.getPrimaryIp() == null) return view;
        for (EnvironmentPortAllocationRecord port : ports.selectByEnvironment(environment.getEnvironmentId())) {
            String address = (Integer.valueOf(9090).equals(port.getContainerPort()) ? "https://" : "http://") + agent.getPrimaryIp() + ":" + port.getHostPort();
            if ("ANNOTATION".equals(port.getComponentType())) view.put("annotationUrl", address);
            else if (Integer.valueOf(9090).equals(port.getContainerPort())) view.put("editorUrl", address);
            else if (Integer.valueOf(8888).equals(port.getContainerPort())) view.put("jupyterUrl", address);
            else if (Integer.valueOf(5000).equals(port.getContainerPort()))
                view.put("t100Url", agent.getPrimaryIp() + ":" + port.getHostPort());
        }
        return view;
    }

    private TrainingEnvironmentRecord competitionEnvironment(int userId) {
        TrainingEnvironmentRecord environment = competitionEnvironmentOrNull(userId);
        if (environment == null) throw new IllegalArgumentException("当前账号未创建比赛实训环境");
        return environment;
    }

    private TrainingEnvironmentRecord competitionEnvironmentOrNull(int userId) {
        if (operations == null) return null;
        return operations.listForUser(userId).stream()
                .filter(row -> "COMPETITION".equals(row.getEnvironmentType()))
                .findFirst()
                .orElse(null);
    }
}
