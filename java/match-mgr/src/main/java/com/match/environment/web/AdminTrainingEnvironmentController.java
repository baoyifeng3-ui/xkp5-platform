package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.service.EnvironmentOperationService;
import com.match.environment.service.TrainingEnvironmentService;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.model.CreateTrainingEnvironmentRequest;
import com.match.environment.model.CreateAccountsEnvironmentRequest;
import com.match.environment.model.ClassStartRequest;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/admin/training-environments")
public class AdminTrainingEnvironmentController {
    private final RoleGuard roleGuard;
    private final EnvironmentOperationService operationService;
    private final TrainingEnvironmentService environmentService;
    private final ProcessingEnvironmentSlotMapper slotMapper;
    private ContainerTemplateMapper templateMapper;
    private EnvironmentPortAllocationMapper portMapper;
    private ProcessingAgentMapper agentMapper;
    private com.match.environment.service.ActiveClassSessionService classSessionService;
    private com.match.environment.service.AccountEnvironmentCreationService accountCreationService;
    private com.match.account.service.AccountSlotService accountSlotService;

    public AdminTrainingEnvironmentController(RoleGuard roleGuard,
                                              EnvironmentOperationService operationService) {
        this(roleGuard, operationService, null, null);
    }

    @Autowired public void setTemplateMapper(ContainerTemplateMapper templateMapper){this.templateMapper=templateMapper;}
    @Autowired public void setPortMapper(EnvironmentPortAllocationMapper portMapper){this.portMapper=portMapper;}
    @Autowired public void setAgentMapper(ProcessingAgentMapper agentMapper){this.agentMapper=agentMapper;}
    @Autowired public void setClassSessionService(com.match.environment.service.ActiveClassSessionService value){this.classSessionService=value;}
    @Autowired public void setAccountCreationService(com.match.environment.service.AccountEnvironmentCreationService value){this.accountCreationService=value;}
    @Autowired public void setAccountSlotService(com.match.account.service.AccountSlotService value){this.accountSlotService=value;}

    @GetMapping("/eligible-accounts")
    public ResponseResult<Object> eligibleAccounts(){roleGuard.requireAnyAdmin();return Response.makeOKRsp(accountSlotService.eligible());}

    @PostMapping("/{environmentId}/class/start")
    public ResponseResult<Object> startClass(@PathVariable String environmentId) {
        User actor=roleGuard.requireAnyAdmin();classSessionService.start(environmentId,actor.getUserId());
        return Response.makeOKRsp(operationService.start(environmentId,actor.getUserId(),roleGuard.roleOf(actor).name()));
    }

    @PostMapping("/class/start-course")
    public ResponseResult<Object> startCourseClass(@RequestBody ClassStartRequest request) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(classSessionService.startCourse(request.getCourseId(), request.getEditorTool(),
                Boolean.TRUE.equals(request.getIgnoreOffline()), actor.getUserId(), roleGuard.roleOf(actor).name()));
    }

    @PostMapping("/class/start-independent")
    public ResponseResult<Object> startIndependentClass(@RequestBody ClassStartRequest request) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(classSessionService.startIndependent(request.getEnvironmentName(), request.getEditorTool(),
                Boolean.TRUE.equals(request.getIgnoreOffline()), actor.getUserId(), roleGuard.roleOf(actor).name()));
    }

    @GetMapping("/class/status")
    public ResponseResult<Object> classStatus() {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(classSessionService.status());
    }

    @PostMapping("/class/stop")
    public ResponseResult<Object> stopClass() {
        User actor=roleGuard.requireAnyAdmin();return Response.makeOKRsp(classSessionService.stop(actor.getUserId()));
    }

    @GetMapping("/templates")
    public ResponseResult<Object> templates(){roleGuard.requireAnyAdmin();return Response.makeOKRsp(templateMapper.selectAllVersions().stream().filter(item->Boolean.TRUE.equals(item.getEnabled())).collect(java.util.stream.Collectors.toList()));}

    @GetMapping("/available-ports")
    public ResponseResult<Object> availablePorts(@org.springframework.web.bind.annotation.RequestParam String agentId,
                                                 @org.springframework.web.bind.annotation.RequestParam(defaultValue="COURSE") String environmentType,
                                                 @org.springframework.web.bind.annotation.RequestParam(required=false) Integer userId){
        roleGuard.requireAnyAdmin();java.util.Set<Integer> used=new java.util.HashSet<>();com.match.environment.persistence.ProcessingEnvironmentSlotRecord own=userId==null?null:slotMapper.selectByAgentAndUserForUpdate(agentId,userId);for(com.match.environment.persistence.EnvironmentPortAllocationRecord row:portMapper.selectByAgent(agentId))if(own==null||!own.getSlotId().equals(row.getSlotId()))used.add(row.getHostPort());
        java.util.Map<String,Object> result=new java.util.LinkedHashMap<>();boolean competition="COMPETITION".equalsIgnoreCase(environmentType);
        result.put("annotation",free(competition?new int[]{8091,8092,8093,8094}:new int[]{8081,8082,8083,8084},used));
        result.put("vscode",free(competition?new int[]{9191,9192,9193,9194}:new int[]{9091,9092,9093,9094},used));
        result.put("jupyter",free(competition?new int[]{9991,9992,9993,9994}:new int[]{8881,8882,8883,8884},used));
        result.put("t100",free(competition?new int[]{5501,5502,5503,5504}:new int[]{5001,5002,5003,5004},used));return Response.makeOKRsp(result);
    }

    private java.util.List<Integer> free(int[] ports,java.util.Set<Integer> used){java.util.List<Integer> result=new java.util.ArrayList<>();for(int port:ports)if(!used.contains(port))result.add(port);return result;}

    @Autowired
    public AdminTrainingEnvironmentController(RoleGuard roleGuard,
                                              EnvironmentOperationService operationService,
                                              TrainingEnvironmentService environmentService,
                                              ProcessingEnvironmentSlotMapper slotMapper) {
        this.roleGuard = roleGuard;
        this.operationService = operationService;
        this.environmentService = environmentService;
        this.slotMapper = slotMapper;
    }

    @GetMapping
    public ResponseResult<Object> list() {
        roleGuard.requireAnyAdmin();
        java.util.List<java.util.Map<String,Object>> result=new java.util.ArrayList<>();for(com.match.environment.persistence.TrainingEnvironmentRecord row:operationService.listAll()){java.util.Map<String,Object> view=new java.util.LinkedHashMap<>();view.put("environmentId",row.getEnvironmentId());view.put("environmentName",row.getEnvironmentName());view.put("environmentType",row.getEnvironmentType());view.put("userId",row.getUserId());view.put("courseId",row.getCourseId());view.put("agentId",row.getAgentId());view.put("slotNumber",row.getSlotNumber());view.put("actualState",row.getActualState());view.put("desiredState",row.getDesiredState());com.match.agent.persistence.ProcessingAgentRecord agent=agentMapper.selectForManagement(row.getAgentId());String host=agent==null?null:agent.getPrimaryIp();for(com.match.environment.persistence.EnvironmentPortAllocationRecord port:portMapper.selectByEnvironment(row.getEnvironmentId())){if(host==null)continue;String url=Integer.valueOf(9090).equals(port.getContainerPort())?"https://":"http://"+host+":"+port.getHostPort();if("ANNOTATION".equals(port.getComponentType()))view.put("annotationUrl",url);else if(Integer.valueOf(9090).equals(port.getContainerPort()))view.put("editorUrl",url);else if(Integer.valueOf(8888).equals(port.getContainerPort()))view.put("jupyterUrl",url);else if(Integer.valueOf(5000).equals(port.getContainerPort()))view.put("t100Url",host+":"+port.getHostPort());}result.add(view);}return Response.makeOKRsp(result);
    }

    @GetMapping("/slots")
    public ResponseResult<Object> slots() {
        roleGuard.requireAnyAdmin();
        if (slotMapper == null) return Response.makeOKRsp(java.util.Collections.emptyList());
        java.util.List<com.match.environment.persistence.ProcessingEnvironmentSlotRecord> available = new java.util.ArrayList<>();
        for (com.match.environment.persistence.ProcessingEnvironmentSlotRecord slot : slotMapper.selectAll()) {
            if (slot.getUserId() != null) continue;
            com.match.agent.persistence.ProcessingAgentRecord agent = agentMapper == null ? null : agentMapper.selectForManagement(slot.getAgentId());
            if (agent != null && Boolean.TRUE.equals(agent.getEnabled()) && agent.getRemovedAt() == null
                    && agent.getLastSeenAt() != null && agent.getLastSeenAt().isAfter(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(30))) available.add(slot);
        }
        return Response.makeOKRsp(available);
    }

    @PostMapping
    public ResponseResult<Object> create(@RequestBody CreateAccountsEnvironmentRequest request) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(accountCreationService.create(request,actor.getUserId(),roleGuard.roleOf(actor).name()));
    }

    @PostMapping("/{environmentId}/start")
    public ResponseResult<Object> start(@PathVariable String environmentId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(operationService.start(environmentId, actor.getUserId(),
                roleGuard.roleOf(actor).name()));
    }

    @PostMapping("/{environmentId}/stop")
    public ResponseResult<Object> stop(@PathVariable String environmentId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(operationService.stop(environmentId, actor.getUserId(),
                roleGuard.roleOf(actor).name()));
    }

    @DeleteMapping("/{environmentId}")
    public ResponseResult<Object> delete(@PathVariable String environmentId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(operationService.delete(environmentId, actor.getUserId(),
                roleGuard.roleOf(actor).name()));
    }

    @PostMapping("/{environmentId}/restore")
    public ResponseResult<Object> restore(@PathVariable String environmentId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(operationService.restore(environmentId, actor.getUserId(),
                roleGuard.roleOf(actor).name()));
    }
}
