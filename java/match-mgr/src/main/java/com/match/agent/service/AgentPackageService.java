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
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.zip.GZIPOutputStream;
import com.match.terminal.service.SshBridgeService;

@Service
public class AgentPackageService {
    public static final String AGENT_VERSION = "0.2.35";
    private final RegistrationTokenService tokenService;
    private final Path packageRoot;
    private final Path caFile;
    private final String managementUrl;
    private final Path codeServerCaFile;
    private final Path codeServerRootCaFile;
    private SshBridgeService sshBridgeService;

    public AgentPackageService(RegistrationTokenService tokenService,
                               @Value("${xkp.agent.package-root:/opt/xkp-agent-package}") String packageRoot,
                               @Value("${xkp.agent.ca-file:/etc/xkp/agent-tls/ca.crt}") String caFile,
                               @Value("${xkp.agent.management-url:https://172.16.33.182:19443}") String managementUrl,
                               @Value("${xkp.code-server.ca-key-file:/etc/xkp/code-server-tls/ca.key}") String codeServerCaFile,
                               @Value("${xkp.code-server.root-ca-file:/etc/xkp/code-server-tls/rootCA.pem}") String codeServerRootCaFile) {
        this.tokenService = tokenService;
        this.packageRoot = Paths.get(packageRoot).toAbsolutePath().normalize();
        this.caFile = Paths.get(caFile).toAbsolutePath().normalize();
        this.managementUrl = managementUrl;
        this.codeServerCaFile = Paths.get(codeServerCaFile).toAbsolutePath().normalize();
        this.codeServerRootCaFile = Paths.get(codeServerRootCaFile).toAbsolutePath().normalize();
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setSshBridgeService(SshBridgeService sshBridgeService) { this.sshBridgeService = sshBridgeService; }

    public PackageArtifact build(Integer actorUserId, AgentPackageRequest request) {
        validate(request);
        Path binary = packageRoot.resolve("dist/xkp-agent-linux-amd64");
        if (!Files.isRegularFile(binary)) {
            throw new IllegalArgumentException("Agent Linux 二进制不存在，请先配置 Agent 部署包目录");
        }
        try {
            String binarySignature = new String(Files.readAllBytes(binary), StandardCharsets.ISO_8859_1);
            if (binarySignature.contains("GLIBC_2.32") || binarySignature.contains("GLIBC_2.34")) {
                throw new IllegalArgumentException("Agent 二进制需要过高 glibc，请使用 deploy/build-linux.sh 重新构建静态版本");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Agent 二进制检查失败", exception);
        }
        if (!Files.isRegularFile(caFile)) {
            throw new IllegalArgumentException("Agent CA 证书不存在，请先配置管理平台 Agent CA");
        }
        if (!Files.isRegularFile(codeServerCaFile) || !Files.isRegularFile(codeServerRootCaFile)) {
            throw new IllegalArgumentException("Code-server TLS 根证书不可用");
        }
        if (sshBridgeService == null) throw new IllegalStateException("管理服务器 SSH 服务不可用");
        String managementSshPublicKey = sshBridgeService.publicKey();
        RegistrationTokenView token = tokenService.create(actorUserId, request.getLabel());
        String displayName = request.getLabel().trim();
        String workspace = request.getWorkspace() == null || request.getWorkspace().trim().isEmpty()
                ? "/srv/xkp" : request.getWorkspace().trim();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Path leafDir = null;
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            leafDir = codeServerLeaf(request.getServerIp());
            addFile(gzip, "dist/xkp-agent-linux-amd64", packageRoot.resolve("dist/xkp-agent-linux-amd64"), 0755);
            addFile(gzip, "deploy/install.sh", packageRoot.resolve("deploy/install.sh"), 0755);
            addFile(gzip, "deploy/one-click-install.sh", packageRoot.resolve("deploy/one-click-install.sh"), 0755);
            addFile(gzip, "deploy/build-linux.sh", packageRoot.resolve("deploy/build-linux.sh"), 0755);
            addFile(gzip, "deploy/verify.sh", packageRoot.resolve("deploy/verify.sh"), 0755);
            addFile(gzip, "deploy/xkp-agent.service", packageRoot.resolve("deploy/xkp-agent.service"), 0644);
            addFile(gzip, "deploy/ca.crt", caFile, 0644);
            addFile(gzip, "deploy/code-cert.pem", leafDir.resolve("code-cert.pem"), 0644);
            addFile(gzip, "deploy/code-cert-key.pem", leafDir.resolve("code-cert-key.pem"), 0600);
            addText(gzip, "deploy/management-ssh.pub", managementSshPublicKey + "\n", 0644);
            addText(gzip, "install-server.sh", wrapper(token.getToken(), displayName, workspace), 0755);
            addText(gzip, "INSTALL.txt", instructions(displayName, request.getServerIp()), 0644);
            gzip.write(new byte[1024]);
        } catch (IOException exception) {
            throw new IllegalStateException("Agent 部署包生成失败", exception);
        } finally {
            if (leafDir != null) try { Files.walk(leafDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); } catch (IOException ignored) { }
        }
        return new PackageArtifact(output.toByteArray(), safeFileName(displayName) + "-xkp-agent.tar.gz");
    }

    private String wrapper(String token, String displayName, String workspace) {
        return "#!/usr/bin/env bash\nset -Eeuo pipefail\ncd \"$(dirname \"$0\")\"\n"
                + "# Restore permissions defensively when the package is extracted by a tool that ignores tar modes.\n"
                + "chmod +x dist/xkp-agent-linux-amd64 deploy/install.sh deploy/verify.sh\n"
                + "ssh_user=\"${SUDO_USER:-$(id -un)}\"\n"
                + "ssh_home=\"$(getent passwd \"$ssh_user\" | cut -d: -f6)\"\n"
                + "[[ -n \"$ssh_home\" ]] || { echo 'ERROR: SSH user home not found' >&2; exit 1; }\n"
                + "install -d -m 700 -o \"$ssh_user\" -g \"$(id -gn \"$ssh_user\")\" \"$ssh_home/.ssh\"\n"
                + "touch \"$ssh_home/.ssh/authorized_keys\"\n"
                + "grep -qxFf deploy/management-ssh.pub \"$ssh_home/.ssh/authorized_keys\" || cat deploy/management-ssh.pub >> \"$ssh_home/.ssh/authorized_keys\"\n"
                + "chown \"$ssh_user:$(id -gn \"$ssh_user\")\" \"$ssh_home/.ssh/authorized_keys\"\n"
                + "chmod 600 \"$ssh_home/.ssh/authorized_keys\"\n"
                + "exec bash deploy/install.sh --binary dist/xkp-agent-linux-amd64 "
                + "--management-url " + shellQuote(managementUrl) + " --ca deploy/ca.crt "
                + "--code-server-cert deploy/code-cert.pem --code-server-key deploy/code-cert-key.pem "
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

    private Path codeServerLeaf(String ip) {
        if (ip == null || !ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) throw new IllegalArgumentException("处理服务器 IP 无效");
        String[] octets = ip.split("\\."); for (String octet : octets) if (Integer.parseInt(octet) > 255) throw new IllegalArgumentException("处理服务器 IP 无效");
        try {
            Path dir = Files.createTempDirectory("xkp-code-server-leaf-"); Path config = dir.resolve("openssl.cnf");
            Files.write(config, ("[req]\ndistinguished_name=dn\nreq_extensions=ext\nprompt=no\n[dn]\nCN=" + ip + "\n[ext]\nsubjectAltName=IP:" + ip + "\nextendedKeyUsage=serverAuth\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
            run("openssl", "genrsa", "-out", dir.resolve("code-cert-key.pem").toString(), "3072");
            run("openssl", "req", "-new", "-key", dir.resolve("code-cert-key.pem").toString(), "-out", dir.resolve("leaf.csr").toString(), "-config", config.toString());
            run("openssl", "x509", "-req", "-sha256", "-days", "825", "-in", dir.resolve("leaf.csr").toString(), "-CA", codeServerRootCaFile.toString(), "-CAkey", codeServerCaFile.toString(), "-CAcreateserial", "-out", dir.resolve("code-cert.pem").toString(), "-extfile", config.toString(), "-extensions", "ext");
            return dir;
        } catch (Exception error) { throw new IllegalStateException("Code-server 证书签发失败", error); }
    }

    private static void run(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start(); byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) throw new IOException(new String(output, StandardCharsets.UTF_8));
    }

    public Path upgradeBinary() {
        Path binary = packageRoot.resolve("dist/xkp-agent-linux-amd64");
        if (!Files.isRegularFile(binary)) throw new IllegalArgumentException("Agent 升级二进制不存在");
        return binary;
    }

    public String upgradeSha256() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(upgradeBinary())) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest.digest()) value.append(String.format("%02x", item));
            return value.toString();
        } catch (Exception exception) { throw new IllegalStateException("Agent 升级摘要计算失败", exception); }
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
