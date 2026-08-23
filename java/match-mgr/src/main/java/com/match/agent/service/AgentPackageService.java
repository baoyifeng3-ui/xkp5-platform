package com.match.agent.service;

import com.match.agent.model.AgentPackageRequest;
import com.match.agent.model.RegistrationTokenView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AgentPackageService {
    private final RegistrationTokenService tokenService;
    private final Path packageRoot;
    private final Path caFile;
    private final String managementUrl;

    public AgentPackageService(RegistrationTokenService tokenService,
                               @Value("${xkp.agent.package-root:/opt/xkp-agent-package}") String packageRoot,
                               @Value("${xkp.agent.ca-file:/etc/xkp/agent-tls/ca.crt}") String caFile,
                               @Value("${xkp.agent.management-url:https://172.16.33.182:19443}") String managementUrl) {
        this.tokenService = tokenService;
        this.packageRoot = Paths.get(packageRoot).toAbsolutePath().normalize();
        this.caFile = Paths.get(caFile).toAbsolutePath().normalize();
        this.managementUrl = managementUrl;
    }

    public PackageArtifact build(Integer actorUserId, AgentPackageRequest request) {
        validate(request);
        if (!Files.isRegularFile(packageRoot.resolve("dist/xkp-agent-linux-amd64"))) {
            throw new IllegalArgumentException("Agent Linux 二进制不存在，请先配置 Agent 部署包目录");
        }
        if (!Files.isRegularFile(caFile)) {
            throw new IllegalArgumentException("Agent CA 证书不存在，请先配置管理平台 Agent CA");
        }
        RegistrationTokenView token = tokenService.create(actorUserId, request.getLabel());
        String displayName = request.getLabel().trim();
        String workspace = request.getWorkspace() == null || request.getWorkspace().trim().isEmpty()
                ? "/srv/xkp" : request.getWorkspace().trim();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addFile(zip, "dist/xkp-agent-linux-amd64", packageRoot.resolve("dist/xkp-agent-linux-amd64"));
            addFile(zip, "deploy/install.sh", packageRoot.resolve("deploy/install.sh"));
            addFile(zip, "deploy/one-click-install.sh", packageRoot.resolve("deploy/one-click-install.sh"));
            addFile(zip, "deploy/verify.sh", packageRoot.resolve("deploy/verify.sh"));
            addFile(zip, "deploy/ca.crt", caFile);
            addText(zip, "install-server.sh", wrapper(token.getToken(), displayName, workspace));
            addText(zip, "INSTALL.txt", instructions(displayName, request.getServerIp()));
        } catch (IOException exception) {
            throw new IllegalStateException("Agent 部署包生成失败", exception);
        }
        return new PackageArtifact(output.toByteArray(), safeFileName(displayName) + "-xkp-agent.zip");
    }

    private String wrapper(String token, String displayName, String workspace) {
        return "#!/usr/bin/env bash\nset -Eeuo pipefail\ncd \"$(dirname \"$0\")\"\nexec bash deploy/install.sh --binary dist/xkp-agent-linux-amd64 "
                + "--management-url " + shellQuote(managementUrl) + " --ca deploy/ca.crt "
                + "--registration-token " + shellQuote(token) + " --display-name "
                + shellQuote(displayName) + " --workspace " + shellQuote(workspace) + "\n";
    }

    private String instructions(String displayName, String serverIp) {
        return "XKP5 Agent 部署包\n\n服务器：" + serverIp + "\n备注：" + displayName
                + "\n\n在 Ubuntu 处理服务器执行：\n  chmod +x install-server.sh\n  sudo bash install-server.sh\n\n部署包内包含一次性凭据，请勿转发或重复使用。\n";
    }

    private void addFile(ZipOutputStream zip, String name, Path source) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) zip.write(buffer, 0, read);
        }
        zip.closeEntry();
    }

    private void addText(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void validate(AgentPackageRequest request) {
        if (request == null || request.getServerIp() == null || request.getServerIp().trim().isEmpty()
                || request.getLabel() == null || request.getLabel().trim().isEmpty()) {
            throw new IllegalArgumentException("服务器 IP 和备注名称不能为空");
        }
        if (!request.getServerIp().trim().matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
            throw new IllegalArgumentException("服务器 IP 格式无效");
        }
    }

    private String shellQuote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    private String safeFileName(String value) { return value.replaceAll("[^A-Za-z0-9._-]+", "_"); }

    public static final class PackageArtifact {
        private final byte[] content;
        private final String fileName;
        public PackageArtifact(byte[] content, String fileName) { this.content = content; this.fileName = fileName; }
        public byte[] getContent() { return content; }
        public String getFileName() { return fileName; }
    }
}
