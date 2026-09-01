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
import com.match.resource.persistence.CourseResourceCatalogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import com.match.agent.model.AgentCommandFinishedEvent;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Service
public class CourseDeliveryService {
    private final CourseResourceMapper resourceMapper;
    private final CourseDeliveryMapper deliveryMapper;
    private final TrainingEnvironmentMapper environmentMapper;
    private final ProcessingAgentMapper agentMapper;
    private final AgentCommandService commandService;
    private final ObjectMapper objectMapper;
    private final CourseResourceCatalogMapper catalogMapper;

    public CourseDeliveryService(CourseResourceMapper resourceMapper, CourseDeliveryMapper deliveryMapper,
                                 TrainingEnvironmentMapper environmentMapper, ProcessingAgentMapper agentMapper,
                                 AgentCommandService commandService, ObjectMapper objectMapper) {
        this(resourceMapper, deliveryMapper, environmentMapper, agentMapper, commandService,
                objectMapper, null);
    }

    @Autowired
    public CourseDeliveryService(CourseResourceMapper resourceMapper, CourseDeliveryMapper deliveryMapper,
                                 TrainingEnvironmentMapper environmentMapper, ProcessingAgentMapper agentMapper,
                                 AgentCommandService commandService, ObjectMapper objectMapper,
                                 CourseResourceCatalogMapper catalogMapper) {
        this.resourceMapper = resourceMapper;
        this.deliveryMapper = deliveryMapper;
        this.environmentMapper = environmentMapper;
        this.agentMapper = agentMapper;
        this.commandService = commandService;
        this.objectMapper = objectMapper;
        this.catalogMapper = catalogMapper;
    }

    @Transactional
    public CourseDeliveryRecord deliver(String resourceId, String environmentId, Integer userId,
                                        Integer actorUserId, String actorRole) {
        CourseResourceRecord resource = resource(resourceId);
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

    @Transactional
    public List<CourseDeliveryRecord> deliverToAllUsers(String resourceId, Integer actorUserId, String actorRole) {
        CourseResourceRecord resource = resource(resourceId);
        if (resource == null) throw new IllegalArgumentException("课程资源不存在");
        List<CourseDeliveryRecord> result = new ArrayList<>();
        List<String> courseIds = catalogMapper == null
                ? java.util.Collections.singletonList(resource.getCourseId())
                : catalogMapper.selectEnabledCourseIds(resourceId);
        for (TrainingEnvironmentRecord environment : environmentMapper.selectAllEnvironments()) {
            if (environment.getUserId() != null
                    && courseIds.contains(environment.getCourseId())) {
                result.add(deliver(resourceId, environment.getEnvironmentId(), environment.getUserId(), actorUserId, actorRole));
            }
        }
        return result;
    }

    public List<CourseDeliveryRecord> deliveriesForUser(Integer userId) {
        return deliveryMapper.selectByUser(userId);
    }

    public List<CourseDeliveryRecord> recentDeliveries() {
        return deliveryMapper.selectRecent();
    }

    private CourseResourceRecord resource(String resourceId) {
        return catalogMapper == null ? resourceMapper.selectById(resourceId)
                : catalogMapper.selectByFileId(resourceId);
    }

    @EventListener
    @Transactional
    public void onCommandFinished(AgentCommandFinishedEvent event) {
        if (!"DELIVER_COURSE_RESOURCE".equals(event.getCommandType())) return;
        CourseDeliveryRecord delivery = deliveryMapper.selectByCommandId(event.getCommandId());
        if (delivery == null || "SUCCEEDED".equals(delivery.getState()) || "FAILED".equals(delivery.getState())) return;
        delivery.setState(event.isSuccess() ? "SUCCEEDED" : "FAILED");
        delivery.setFailureCode(event.isSuccess() ? null : event.getResultCode());
        delivery.setFailureMessage(event.isSuccess() ? null : event.getResultMessage());
        delivery.setCompletedAt(LocalDateTime.now());
        delivery.setUpdatedAt(LocalDateTime.now());
        deliveryMapper.updateById(delivery);
    }
}
