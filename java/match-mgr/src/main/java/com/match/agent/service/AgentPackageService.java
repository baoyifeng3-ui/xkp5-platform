package com.match.agent.service;

import com.match.agent.model.AgentPackageRequest;
import com.match.agent.model.RegistrationTokenView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPOutputStream;

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
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            addFile(gzip, "dist/xkp-agent-linux-amd64", packageRoot.resolve("dist/xkp-agent-linux-amd64"), 0755);
            addFile(gzip, "deploy/install.sh", packageRoot.resolve("deploy/install.sh"), 0755);
            addFile(gzip, "deploy/one-click-install.sh", packageRoot.resolve("deploy/one-click-install.sh"), 0755);
            addFile(gzip, "deploy/verify.sh", packageRoot.resolve("deploy/verify.sh"), 0755);
            addFile(gzip, "deploy/ca.crt", caFile, 0644);
            addText(gzip, "install-server.sh", wrapper(token.getToken(), displayName, workspace), 0755);
            addText(gzip, "INSTALL.txt", instructions(displayName, request.getServerIp()), 0644);
            gzip.write(new byte[1024]);
        } catch (IOException exception) {
            throw new IllegalStateException("Agent 部署包生成失败", exception);
        }
        return new PackageArtifact(output.toByteArray(), safeFileName(displayName) + "-xkp-agent.tar.gz");
    }

    private String wrapper(String token, String displayName, String workspace) {
        return "#!/usr/bin/env bash\nset -Eeuo pipefail\ncd \"$(dirname \"$0\")\"\n"
                + "# Restore permissions defensively when the package is extracted by a tool that ignores tar modes.\n"
                + "chmod +x dist/xkp-agent-linux-amd64 deploy/install.sh deploy/verify.sh\n"
                + "if [[ ! -d /etc/polkit-1/rules.d ]]; then\n"
                + "  command -v apt-get >/dev/null || { echo 'ERROR: apt-get is required to install polkit' >&2; exit 1; }\n"
                + "  apt-get update\n"
                + "  DEBIAN_FRONTEND=noninteractive apt-get install -y policykit-1\n"
                + "  if apt-cache show polkitd >/dev/null 2>&1; then DEBIAN_FRONTEND=noninteractive apt-get install -y polkitd; fi\n"
                + "  install -d -m 0755 /etc/polkit-1/rules.d\n"
                + "fi\n"
                + "exec bash deploy/install.sh --binary dist/xkp-agent-linux-amd64 "
                + "--management-url " + shellQuote(managementUrl) + " --ca deploy/ca.crt "
                + "--registration-token " + shellQuote(token) + " --display-name "
                + shellQuote(displayName) + " --workspace " + shellQuote(workspace) + "\n";
    }

    private String instructions(String displayName, String serverIp) {
        return "XKP5 Agent 部署包\n\n服务器：" + serverIp + "\n备注：" + displayName
                + "\n\n在 Ubuntu 处理服务器执行：\n  chmod +x install-server.sh\n  sudo bash install-server.sh\n\n部署包内包含一次性凭据，请勿转发或重复使用。\n";
    }

    private void addFile(OutputStream output, String name, Path source, int mode) throws IOException {
        long size = Files.size(source);
        writeHeader(output, name, size, mode);
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[8192];
            int read; long written = 0;
            while ((read = input.read(buffer)) != -1) { output.write(buffer, 0, read); written += read; }
            writePadding(output, written);
        }
    }

    private void addText(OutputStream output, String name, String value, int mode) throws IOException {
        byte[] content = value.getBytes(StandardCharsets.UTF_8);
        writeHeader(output, name, content.length, mode);
        output.write(content);
        writePadding(output, content.length);
    }

    private void writeHeader(OutputStream output, String name, long size, int mode) throws IOException {
        byte[] header = new byte[512];
        writeString(header, 0, 100, name);
        writeOctal(header, 100, 8, mode);
        writeOctal(header, 108, 8, 0);
        writeOctal(header, 116, 8, 0);
        writeOctal(header, 124, 12, size);
        writeOctal(header, 136, 12, 0);
        for (int i = 148; i < 156; i++) header[i] = ' ';
        header[156] = '0';
        writeString(header, 257, 6, "ustar");
        writeString(header, 263, 2, "00");
        long checksum = 0;
        for (int i = 0; i < header.length; i++) {
            checksum += (i >= 148 && i < 156) ? ' ' : header[i] & 0xff;
        }
        String checksumText = String.format("%06o", checksum);
        writeString(header, 148, 6, checksumText);
        header[154] = 0;
        header[155] = ' ';
        output.write(header);
    }

    private void writePadding(OutputStream output, long size) throws IOException {
        int padding = (int) ((512 - (size % 512)) % 512);
        if (padding > 0) output.write(new byte[padding]);
    }

    private void writeString(byte[] target, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, offset, Math.min(bytes.length, length));
    }

    private void writeOctal(byte[] target, int offset, int length, long value) {
        String text = Long.toOctalString(value);
        int digits = Math.min(text.length(), length - 1);
        for (int i = offset; i < offset + length - 1; i++) target[i] = '0';
        int start = offset + length - digits - 1;
        for (int i = 0; i < digits; i++) target[start + i] = (byte) text.charAt(text.length() - digits + i);
        target[offset + length - 1] = 0;
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
