package com.match.environment.service;

import com.match.environment.model.ContainerPortSpec;
import com.match.environment.model.ContainerTemplateRequest;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.licensing.guard.LicenseGuard;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ContainerTemplateServiceTest {
    private ContainerTemplateMapper mapper;
    private LicenseGuard license;
    private ContainerTemplateService service;

    @Before
    public void setUp() {
        mapper = mock(ContainerTemplateMapper.class);
        license = mock(LicenseGuard.class);
        service = new ContainerTemplateService(mapper, license,
                Clock.fixed(Instant.parse("2026-08-19T03:00:00Z"), ZoneOffset.UTC));
        when(mapper.selectPublishedDigest(anyString(), anyString()))
                .thenReturn("sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Test
    public void publishesImmutableAnnotationTemplateVersion() {
        ContainerTemplateRecord published = service.publish(annotation(), 7);

        ArgumentCaptor<ContainerTemplateRecord> saved =
                ArgumentCaptor.forClass(ContainerTemplateRecord.class);
        verify(license).requireActive();
        verify(mapper).insert(saved.capture());
        assertEquals(1, saved.getValue().getTemplateVersion().intValue());
        assertEquals("ANNOTATION", saved.getValue().getComponentType());
        assertEquals("sysbox-runc", saved.getValue().getRuntimeName());
        assertEquals("/root/data", saved.getValue().getMountTarget());
        assertEquals("sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                saved.getValue().getImageReference());
        assertEquals(7, saved.getValue().getCreatedBy().intValue());
        assertNotNull(saved.getValue().getPublishedAt());
        assertEquals(saved.getValue().getTemplateVersionId(), published.getTemplateVersionId());
    }

    @Test
    public void editingPublishedTemplateCreatesNextVersion() {
        ContainerTemplateRecord current = record("template-id", 1);
        when(mapper.selectLatestForUpdate("template-id")).thenReturn(current);
        ContainerTemplateRequest changed = annotation();
        changed.setTemplateId("template-id");
        changed.setImageReference("zy-anno:2");

        ContainerTemplateRecord published = service.publish(changed, 7);

        assertEquals(2, published.getTemplateVersion().intValue());
        assertEquals("template-id", published.getTemplateId());
        assertNotEquals(current.getTemplateVersionId(), published.getTemplateVersionId());
        verify(mapper, never()).updateById(any(ContainerTemplateRecord.class));
    }

    @Test
    public void disabledVersionCannotBeAssigned() {
        ContainerTemplateRecord disabled = record("template-id", 2);
        disabled.setEnabled(false);
        when(mapper.selectVersion("template-id", 2)).thenReturn(disabled);

        expectInvalid(() -> service.requireEnabledVersion("template-id", 2, "ANNOTATION"));
    }

    @Test
    public void rejectsRuntimeMountNetworkPrivilegeAndDuplicatePorts() {
        ContainerTemplateRequest invalidRuntime = annotation();
        invalidRuntime.setRuntimeName("runc --privileged");
        expectInvalid(() -> service.publish(invalidRuntime, 7));

        ContainerTemplateRequest invalidMount = annotation();
        invalidMount.setMountTarget("/etc");
        expectInvalid(() -> service.publish(invalidMount, 7));

        ContainerTemplateRequest privileged = annotation();
        privileged.setPrivileged(true);
        expectInvalid(() -> service.publish(privileged, 7));

        ContainerTemplateRequest hostNetwork = annotation();
        hostNetwork.setHostNetwork(true);
        expectInvalid(() -> service.publish(hostNetwork, 7));

        ContainerTemplateRequest duplicatePorts = annotation();
        duplicatePorts.setPorts(Arrays.asList(port(8080), port(8080)));
        expectInvalid(() -> service.publish(duplicatePorts, 7));
    }

    @Test
    public void validatesComponentDefaultsAndResourceBounds() {
        ContainerTemplateRequest editor = editor();
        editor.setMemoryLimitBytes(6L * 1024 * 1024 * 1024);
        editor.setGpuEnabled(true);
        editor.setGpuComputePercent(50);

        ContainerTemplateRecord published = service.publish(editor, 7);

        assertEquals("EDITOR", published.getComponentType());
        assertEquals("nvidia", published.getRuntimeName());
        assertEquals("/home/student/data", published.getMountTarget());
        assertEquals(Arrays.asList("/bin/bash"), editor.getCommand());

        ContainerTemplateRequest unlimited = editor();
        unlimited.setMemoryLimitBytes(null);
        expectInvalid(() -> service.publish(unlimited, 7));

        ContainerTemplateRequest excessiveGpu = editor();
        excessiveGpu.setGpuEnabled(true);
        excessiveGpu.setGpuComputePercent(101);
        expectInvalid(() -> service.publish(excessiveGpu, 7));
    }

    private ContainerTemplateRequest annotation() {
        ContainerTemplateRequest request = new ContainerTemplateRequest();
        request.setReleaseId("annotation-release");
        request.setTemplateName("Annotation");
        request.setComponentType("ANNOTATION");
        request.setImageReference("zy-anno:latest");
        request.setRuntimeName("sysbox-runc");
        request.setRestartPolicy("always");
        request.setPorts(Collections.singletonList(port(8080)));
        request.setMountTarget("/root/data");
        request.setCpuLimitMillis(2000);
        request.setMemoryLimitBytes(2L * 1024 * 1024 * 1024);
        return request;
    }

    private ContainerTemplateRequest editor() {
        ContainerTemplateRequest request = new ContainerTemplateRequest();
        request.setReleaseId("editor-release");
        request.setTemplateName("Editor");
        request.setComponentType("EDITOR");
        request.setImageReference("zy-contestv2:latest");
        request.setRuntimeName("nvidia");
        request.setRestartPolicy("always");
        request.setPorts(Arrays.asList(port(9090), port(8887), port(5000)));
        request.setMountTarget("/home/student/data");
        request.setCommand(Collections.singletonList("/bin/bash"));
        request.setCpuLimitMillis(4000);
        request.setMemoryLimitBytes(6L * 1024 * 1024 * 1024);
        return request;
    }

    private ContainerPortSpec port(int value) {
        return new ContainerPortSpec(value, "tcp");
    }

    private ContainerTemplateRecord record(String templateId, int version) {
        ContainerTemplateRecord record = new ContainerTemplateRecord();
        record.setTemplateVersionId("version-" + version);
        record.setTemplateId(templateId);
        record.setTemplateVersion(version);
        record.setComponentType("ANNOTATION");
        record.setEnabled(true);
        return record;
    }

    private void expectInvalid(Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertFalse("expected template rejection", !rejected);
    }
}
