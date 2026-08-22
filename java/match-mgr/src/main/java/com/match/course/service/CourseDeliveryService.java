package com.match.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.course.persistence.CourseDeliveryMapper;
import com.match.course.persistence.CourseDeliveryRecord;
import com.match.course.persistence.CourseResourceMapper;
import com.match.course.persistence.CourseResourceRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CourseDeliveryService {
    private final CourseResourceMapper resourceMapper;
    private final CourseDeliveryMapper deliveryMapper;
    private final TrainingEnvironmentMapper environmentMapper;
    private final ProcessingAgentMapper agentMapper;
    private final AgentCommandService commandService;
    private final ObjectMapper objectMapper;

    public CourseDeliveryService(CourseResourceMapper resourceMapper, CourseDeliveryMapper deliveryMapper,
                                 TrainingEnvironmentMapper environmentMapper, ProcessingAgentMapper agentMapper,
                                 AgentCommandService commandService, ObjectMapper objectMapper) {
        this.resourceMapper = resourceMapper;
        this.deliveryMapper = deliveryMapper;
        this.environmentMapper = environmentMapper;
        this.agentMapper = agentMapper;
        this.commandService = commandService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CourseDeliveryRecord deliver(String resourceId, String environmentId, Integer userId,
                                        Integer actorUserId, String actorRole) {
        CourseResourceRecord resource = resourceMapper.selectById(resourceId);
        TrainingEnvironmentRecord environment = environmentMapper.selectForUpdate(environmentId);
        if (resource == null || !Boolean.TRUE.equals(resource.getEnabled())) {
            throw new IllegalArgumentException("课程资源不存在或已停用");
        }
        if (environment == null || environment.getUserId() == null || !userId.equals(environment.getUserId())) {
            throw new IllegalArgumentException("实训环境不存在或不属于目标用户");
        }
        if (!"ADMIN".equals(actorRole) && !"SUPER_ADMIN".equals(actorRole)
                && !("USER".equals(actorRole) && actorUserId.equals(userId))) {
            throw new IllegalArgumentException("无权下发课程资源");
        }
        CourseDeliveryRecord existing = deliveryMapper.selectDeliveryForUpdate(resourceId, userId, environmentId);
        if (existing != null && !"FAILED".equals(existing.getState())) return existing;
        ProcessingAgentRecord agent = agentMapper.selectForManagement(environment.getAgentId());
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled())) throw new IllegalArgumentException("处理服务器不可用");
        LocalDateTime now = LocalDateTime.now();
        CourseDeliveryRecord delivery = existing == null ? new CourseDeliveryRecord() : existing;
        if (existing == null) delivery.setDeliveryId(UUID.randomUUID().toString());
        delivery.setResourceId(resourceId); delivery.setUserId(userId); delivery.setEnvironmentId(environmentId);
        delivery.setDigest(resource.getSha256()); delivery.setState("PENDING"); delivery.setRequestedAt(now); delivery.setUpdatedAt(now);
        if (existing == null) deliveryMapper.insert(delivery); else deliveryMapper.updateById(delivery);
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("environmentId", environmentId); payload.put("resourceId", resourceId);
            payload.put("resourceType", resource.getResourceType()); payload.put("storageKey", resource.getStorageKey());
            payload.put("targetPath", environment.getWorkspaceRelativePath() + "/course-resources/" + resource.getResourceId());
            AgentCommandView command = commandService.requestEnvironmentCommand(agent, "DELIVER_COURSE_RESOURCE",
                    objectMapper.writeValueAsString(payload), actorUserId, actorRole,
                    environmentId + ":RESOURCE:" + resourceId + ":" + resource.getSha256());
            delivery.setCommandId(command.getCommandId()); delivery.setState("DISPATCHED"); delivery.setUpdatedAt(LocalDateTime.now());
            deliveryMapper.updateById(delivery);
            return delivery;
        } catch (Exception error) {
            delivery.setState("FAILED"); delivery.setFailureCode("DISPATCH_FAILED"); delivery.setFailureMessage(error.getMessage()); delivery.setUpdatedAt(LocalDateTime.now()); deliveryMapper.updateById(delivery); throw error instanceof IllegalArgumentException ? (IllegalArgumentException) error : new IllegalStateException("资源下发失败", error);
        }
    }
}
