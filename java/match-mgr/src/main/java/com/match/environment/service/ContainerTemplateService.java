package com.match.environment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.environment.model.ContainerPortSpec;
import com.match.environment.model.ContainerTemplateRequest;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.licensing.crypto.Digests;
import com.match.licensing.guard.LicenseGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ContainerTemplateService {
    private static final Pattern IMAGE_REFERENCE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/:@-]{0,254}");
    private static final long MIN_MEMORY_BYTES = 128L * 1024 * 1024;
    private static final long MAX_MEMORY_BYTES = 512L * 1024 * 1024 * 1024;

    private final ContainerTemplateMapper mapper;
    private final LicenseGuard licenseGuard;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContainerTemplateService(ContainerTemplateMapper mapper, LicenseGuard licenseGuard, Clock clock) {
        this.mapper = mapper;
        this.licenseGuard = licenseGuard;
        this.clock = clock;
    }

    @Transactional
    public ContainerTemplateRecord publish(ContainerTemplateRequest request, int actorUserId) {
        licenseGuard.requireActive();
        NormalizedTemplate normalized = normalize(request);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        String templateId = trimToNull(request.getTemplateId());
        int version = 1;
        if (templateId == null) {
            templateId = UUID.randomUUID().toString();
        } else {
            ContainerTemplateRecord latest = mapper.selectLatestForUpdate(templateId);
            if (latest == null) {
                throw new IllegalArgumentException("容器模板不存在");
            }
            version = latest.getTemplateVersion() + 1;
        }

        ContainerTemplateRecord record = new ContainerTemplateRecord();
        record.setTemplateVersionId(UUID.randomUUID().toString());
        record.setTemplateId(templateId);
        record.setTemplateVersion(version);
        record.setTemplateName(normalized.name);
        record.setComponentType(normalized.componentType);
        record.setEnabled(true);
        record.setImageReference(normalized.imageReference);
        record.setRuntimeName(normalized.runtimeName);
        record.setRestartPolicy(normalized.restartPolicy);
        record.setPortsJson(writeJson(normalized.ports));
        record.setMountTarget(normalized.mountTarget);
        record.setCommandJson(normalized.command == null ? null : writeJson(normalized.command));
        record.setWorkingDirectory(normalized.workingDirectory);
        record.setCpuLimitMillis(normalized.cpuLimitMillis);
        record.setMemoryLimitBytes(normalized.memoryLimitBytes);
        record.setGpuEnabled(normalized.gpuEnabled);
        record.setGpuComputePercent(normalized.gpuComputePercent);
        record.setGpuMemoryLimitBytes(normalized.gpuMemoryLimitBytes);
        record.setConfigFingerprint(fingerprint(record));
        record.setCreatedBy(actorUserId);
        record.setCreatedAt(now);
        record.setPublishedAt(now);
        mapper.insert(record);
        return record;
    }

    @Transactional
    public void disable(String templateId, int version, int actorUserId) {
        licenseGuard.requireActive();
        ContainerTemplateRecord record = mapper.selectVersion(requireId(templateId), requireVersion(version));
        if (record == null) {
            throw new IllegalArgumentException("容器模板版本不存在");
        }
        if (!Boolean.TRUE.equals(record.getEnabled())) {
            return;
        }
        record.setEnabled(false);
        record.setDisabledAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        mapper.updateById(record);
    }

    @Transactional(readOnly = true)
    public ContainerTemplateRecord requireEnabledVersion(String templateId, int version,
                                                          String componentType) {
        ContainerTemplateRecord record = mapper.selectVersion(requireId(templateId), requireVersion(version));
        if (record == null || !Boolean.TRUE.equals(record.getEnabled())) {
            throw new IllegalArgumentException("容器模板版本不可用");
        }
        String requiredType = normalizeComponentType(componentType);
        if (!requiredType.equals(record.getComponentType())) {
            throw new IllegalArgumentException("容器模板组件类型不匹配");
        }
        return record;
    }

    @Transactional(readOnly = true)
    public List<ContainerTemplateRecord> list() {
        return mapper.selectList(new QueryWrapper<ContainerTemplateRecord>()
                .orderByAsc("component_type", "template_name")
                .orderByDesc("template_version"));
    }

    @Transactional(readOnly = true)
    public List<ContainerTemplateRecord> versions(String templateId) {
        return mapper.selectVersions(requireId(templateId));
    }

    private NormalizedTemplate normalize(ContainerTemplateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("容器模板不能为空");
        }
        if (Boolean.TRUE.equals(request.getPrivileged()) || Boolean.TRUE.equals(request.getHostNetwork())) {
            throw new IllegalArgumentException("不允许特权容器或主机网络");
        }

        NormalizedTemplate result = new NormalizedTemplate();
        result.name = requireText(request.getTemplateName(), "模板名称", 80);
        result.componentType = normalizeComponentType(request.getComponentType());
        result.imageReference = requireText(request.getImageReference(), "镜像", 255);
        if (!IMAGE_REFERENCE.matcher(result.imageReference).matches()) {
            throw new IllegalArgumentException("镜像格式不正确");
        }
        result.runtimeName = requireText(request.getRuntimeName(), "运行时", 32);
        result.restartPolicy = requireText(request.getRestartPolicy(), "重启策略", 16);
        if (!Arrays.asList("always", "unless-stopped", "no").contains(result.restartPolicy)) {
            throw new IllegalArgumentException("重启策略不在允许范围内");
        }
        result.mountTarget = requireText(request.getMountTarget(), "挂载目标", 255);
        validateComponentBoundary(result.componentType, result.runtimeName, result.mountTarget);
        result.ports = normalizePorts(request.getPorts());
        result.command = normalizeCommand(request.getCommand());
        result.workingDirectory = optionalText(request.getWorkingDirectory(), "工作目录", 255);
        result.cpuLimitMillis = requireRange(request.getCpuLimitMillis(), 100, 128000, "CPU 限制");
        result.memoryLimitBytes = requireRange(request.getMemoryLimitBytes(), MIN_MEMORY_BYTES,
                MAX_MEMORY_BYTES, "内存限制");
        result.gpuEnabled = Boolean.TRUE.equals(request.getGpuEnabled());
        result.gpuComputePercent = request.getGpuComputePercent();
        result.gpuMemoryLimitBytes = request.getGpuMemoryLimitBytes();
        validateGpu(result);
        return result;
    }

    private void validateComponentBoundary(String componentType, String runtimeName, String mountTarget) {
        if ("ANNOTATION".equals(componentType)) {
            if (!"sysbox-runc".equals(runtimeName) || !"/root/data".equals(mountTarget)) {
                throw new IllegalArgumentException("图像标注模板运行时或挂载目标不正确");
            }
            return;
        }
        if (!"nvidia".equals(runtimeName) || !"/home/student/data".equals(mountTarget)) {
            throw new IllegalArgumentException("代码编辑模板运行时或挂载目标不正确");
        }
    }

    private List<ContainerPortSpec> normalizePorts(List<ContainerPortSpec> ports) {
        if (ports == null || ports.isEmpty() || ports.size() > 16) {
            throw new IllegalArgumentException("容器端口数量不正确");
        }
        List<ContainerPortSpec> normalized = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (ContainerPortSpec port : ports) {
            if (port == null || port.getContainerPort() == null
                    || port.getContainerPort() < 1 || port.getContainerPort() > 65535) {
                throw new IllegalArgumentException("容器端口不正确");
            }
            String protocol = trimToNull(port.getProtocol());
            protocol = protocol == null ? "tcp" : protocol.toLowerCase(Locale.ROOT);
            if (!"tcp".equals(protocol) && !"udp".equals(protocol)) {
                throw new IllegalArgumentException("端口协议不正确");
            }
            if (!unique.add(port.getContainerPort() + "/" + protocol)) {
                throw new IllegalArgumentException("容器端口重复");
            }
            normalized.add(new ContainerPortSpec(port.getContainerPort(), protocol));
        }
        return normalized;
    }

    private List<String> normalizeCommand(List<String> command) {
        if (command == null || command.isEmpty()) {
            return null;
        }
        if (command.size() > 16) {
            throw new IllegalArgumentException("启动参数过多");
        }
        List<String> normalized = new ArrayList<>();
        for (String argument : command) {
            if (argument == null || argument.isEmpty() || argument.length() > 512
                    || argument.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("启动参数不正确");
            }
            normalized.add(argument);
        }
        return normalized;
    }

    private void validateGpu(NormalizedTemplate template) {
        if (!template.gpuEnabled) {
            if (template.gpuComputePercent != null || template.gpuMemoryLimitBytes != null) {
                throw new IllegalArgumentException("未启用 GPU 时不能设置 GPU 限制");
            }
            return;
        }
        if (!"EDITOR".equals(template.componentType) || !"nvidia".equals(template.runtimeName)) {
            throw new IllegalArgumentException("只有代码编辑模板可以启用 GPU");
        }
        template.gpuComputePercent = requireRange(template.gpuComputePercent, 1, 100, "GPU 算力限制");
        if (template.gpuMemoryLimitBytes != null) {
            template.gpuMemoryLimitBytes = requireRange(template.gpuMemoryLimitBytes,
                    256L * 1024 * 1024, 64L * 1024 * 1024 * 1024, "GPU 显存限制");
        }
    }

    private String fingerprint(ContainerTemplateRecord record) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("componentType", record.getComponentType());
        content.put("image", record.getImageReference());
        content.put("runtime", record.getRuntimeName());
        content.put("restart", record.getRestartPolicy());
        content.put("ports", record.getPortsJson());
        content.put("mount", record.getMountTarget());
        content.put("command", record.getCommandJson());
        content.put("workingDirectory", record.getWorkingDirectory());
        content.put("cpu", record.getCpuLimitMillis());
        content.put("memory", record.getMemoryLimitBytes());
        content.put("gpu", record.getGpuEnabled());
        content.put("gpuCompute", record.getGpuComputePercent());
        content.put("gpuMemory", record.getGpuMemoryLimitBytes());
        return Digests.sha256(writeJson(content));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("容器模板无法序列化", exception);
        }
    }

    private String normalizeComponentType(String value) {
        String type = requireText(value, "组件类型", 16).toUpperCase(Locale.ROOT);
        if (!"ANNOTATION".equals(type) && !"EDITOR".equals(type)) {
            throw new IllegalArgumentException("组件类型不正确");
        }
        return type;
    }

    private String requireId(String value) {
        return requireText(value, "模板编号", 36);
    }

    private int requireVersion(int version) {
        if (version < 1 || version > 65535) {
            throw new IllegalArgumentException("模板版本不正确");
        }
        return version;
    }

    private String requireText(String value, String label, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "不正确");
        }
        return normalized;
    }

    private String optionalText(String value, String label, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "不正确");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int requireRange(Integer value, int minimum, int maximum, String label) {
        if (value == null || value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + "不正确");
        }
        return value;
    }

    private long requireRange(Long value, long minimum, long maximum, String label) {
        if (value == null || value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + "不正确");
        }
        return value;
    }

    private static final class NormalizedTemplate {
        private String name;
        private String componentType;
        private String imageReference;
        private String runtimeName;
        private String restartPolicy;
        private List<ContainerPortSpec> ports;
        private String mountTarget;
        private List<String> command;
        private String workingDirectory;
        private Integer cpuLimitMillis;
        private Long memoryLimitBytes;
        private boolean gpuEnabled;
        private Integer gpuComputePercent;
        private Long gpuMemoryLimitBytes;
    }
}
