package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.service.EnvironmentOperationService;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/training-environments")
public class UserTrainingEnvironmentController {
    private final RoleGuard roleGuard;
    private final EnvironmentOperationService operationService;
    private final EnvironmentPortAllocationMapper portMapper;
    private final ProcessingAgentMapper agentMapper;
    private com.match.environment.service.ActiveClassSessionService classSessionService;

    public UserTrainingEnvironmentController(RoleGuard roleGuard,
                                             EnvironmentOperationService operationService) {
        this(roleGuard, operationService, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setClassSessionService(com.match.environment.service.ActiveClassSessionService value){this.classSessionService=value;}

    @org.springframework.beans.factory.annotation.Autowired
    public UserTrainingEnvironmentController(RoleGuard roleGuard,
                                             EnvironmentOperationService operationService,
                                             EnvironmentPortAllocationMapper portMapper,
                                             ProcessingAgentMapper agentMapper) {
        this.roleGuard = roleGuard;
        this.operationService = operationService;
        this.portMapper = portMapper;
        this.agentMapper = agentMapper;
    }

    @GetMapping
    public ResponseResult<Object> list(@org.springframework.web.bind.annotation.RequestParam(defaultValue="false") boolean defaultOnly) {
        User user = roleGuard.requireUser();
        List<com.match.environment.persistence.TrainingEnvironmentRecord> rows = operationService.listForUser(user.getUserId());
        com.match.environment.persistence.ActiveClassSessionRecord session=classSessionService==null?null:classSessionService.current();
        if(session!=null&&Boolean.TRUE.equals(session.getActive())) {
            if (session.getCourseId() != null) rows=rows.stream().filter(row->session.getCourseId().equals(row.getCourseId())).collect(java.util.stream.Collectors.toList());
            else if (session.getEnvironmentId() != null) rows=rows.stream().filter(row->session.getEnvironmentId().equals(row.getEnvironmentId())).collect(java.util.stream.Collectors.toList());
            else rows=rows.stream().filter(row->row.getCourseId()==null && "COURSE".equals(row.getEnvironmentType())
                    && (session.getEnvironmentName()==null || session.getEnvironmentName().equals(row.getEnvironmentName()))).collect(java.util.stream.Collectors.toList());
        }
        else if(defaultOnly)rows=rows.stream().filter(row->row.getCourseId()==null&&"COURSE".equals(row.getEnvironmentType())).collect(java.util.stream.Collectors.toList());
        List<Map<String, Object>> result = new ArrayList<>();
        for (com.match.environment.persistence.TrainingEnvironmentRecord row : rows) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("environmentId", row.getEnvironmentId()); view.put("environmentName", row.getEnvironmentName());
            view.put("environmentType", row.getEnvironmentType()); view.put("courseId", row.getCourseId());
            view.put("userId", row.getUserId()); view.put("userName", user.getUserName());
            view.put("agentId", row.getAgentId()); view.put("slotNumber", row.getSlotNumber());
            view.put("actualState", row.getActualState()); view.put("desiredState", row.getDesiredState());
            if (session != null && Boolean.TRUE.equals(session.getActive())) view.put("editorTool", session.getEditorTool());
            if (portMapper != null && agentMapper != null) {
                ProcessingAgentRecord agent = agentMapper.selectForManagement(row.getAgentId());
                String host = agent == null ? null : agent.getPrimaryIp();
                for (EnvironmentPortAllocationRecord port : portMapper.selectByEnvironment(row.getEnvironmentId())) {
                    if (host == null) continue;
                    String url = Integer.valueOf(9090).equals(port.getContainerPort()) ? "https://" + host + ":" + port.getHostPort() : "http://" + host + ":" + port.getHostPort();
                    if ("ANNOTATION".equals(port.getComponentType())) view.put("annotationUrl", url);
                    else if (Integer.valueOf(9090).equals(port.getContainerPort())) view.put("editorUrl", url);
                    else if (Integer.valueOf(8888).equals(port.getContainerPort())) view.put("jupyterUrl", url);
                    else if (Integer.valueOf(5000).equals(port.getContainerPort())) view.put("t100Url", host + ":" + port.getHostPort());
                }
            }
            result.add(view);
        }
        return Response.makeOKRsp(result);
    }

    @PostMapping("/{environmentId}/start")
    public ResponseResult<Object> start(@PathVariable String environmentId) {
        User user = roleGuard.requireUser();
        com.match.environment.persistence.ActiveClassSessionRecord session=classSessionService==null?null:classSessionService.current();
        if(session!=null&&Boolean.TRUE.equals(session.getActive())) {
            com.match.environment.persistence.TrainingEnvironmentRecord selected=operationService.listForUser(user.getUserId()).stream()
                    .filter(row->environmentId.equals(row.getEnvironmentId())).findFirst().orElse(null);
            boolean allowed=selected!=null&&(
                    environmentId.equals(session.getEnvironmentId())
                    || (session.getCourseId()!=null&&session.getCourseId().equals(selected.getCourseId()))
                    || (session.getCourseId()==null&&session.getEnvironmentId()==null&&selected.getCourseId()==null
                    && session.getEnvironmentName()!=null&&session.getEnvironmentName().equals(selected.getEnvironmentName())));
            if(!allowed)throw new IllegalArgumentException("上课期间只能使用管理员指定的实训环境");
        }
        return Response.makeOKRsp(operationService.start(environmentId, user.getUserId(), "USER"));
    }
}
