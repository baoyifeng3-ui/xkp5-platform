package com.match.agent.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.match.agent.model.AgentPackageRequest;
import com.match.agent.model.RemoteAgentDeployRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RemoteAgentDeploymentService {
    private final AgentPackageService packageService;

    public RemoteAgentDeploymentService(AgentPackageService packageService) { this.packageService = packageService; }

    public Map<String, Object> deploy(int actorId, RemoteAgentDeployRequest request) {
        validate(request);
        Session session = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(request.getUsername().trim(), request.getServerIp().trim(), request.getSshPort());
            session.setPassword(request.getPassword());
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(15000);

            AgentPackageRequest packageRequest = new AgentPackageRequest();
            packageRequest.setServerIp(request.getServerIp()); packageRequest.setLabel(request.getLabel()); packageRequest.setWorkspace(request.getWorkspace());
            AgentPackageService.PackageArtifact artifact = packageService.build(actorId, packageRequest);
            String remoteDir = "/tmp/xkp-agent-deploy-" + UUID.randomUUID().toString();
            exec(session, "mkdir -p " + quote(remoteDir));
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(10000);
            try (InputStream input = new ByteArrayInputStream(artifact.getContent())) {
                sftp.put(input, remoteDir + "/agent.tar.gz");
            } finally { sftp.disconnect(); }
            String command = "set -e; cd " + quote(remoteDir)
                    + "; tar -xzf agent.tar.gz"
                    + "; ssh_user=" + quote(request.getUsername().trim())
                    + "; ssh_home=\"$(getent passwd \"$ssh_user\" | cut -d: -f6)\""
                    + "; test -n \"$ssh_home\""
                    + "; install -d -m 700 -o \"$ssh_user\" -g \"$(id -gn \"$ssh_user\")\" \"$ssh_home/.ssh\""
                    + "; touch \"$ssh_home/.ssh/authorized_keys\""
                    + "; grep -qxFf deploy/management-ssh.pub \"$ssh_home/.ssh/authorized_keys\" || cat deploy/management-ssh.pub >> \"$ssh_home/.ssh/authorized_keys\""
                    + "; chown \"$ssh_user:$(id -gn \"$ssh_user\")\" \"$ssh_home/.ssh/authorized_keys\""
                    + "; chmod 600 \"$ssh_home/.ssh/authorized_keys\""
                    + "; chmod +x install-server.sh; sudo -S -p '' bash install-server.sh"
                    + "; sudo -S -p '' systemctl restart xkp-agent"
                    + "; sudo -S -p '' systemctl is-active --quiet xkp-agent"
                    + "; printf '\nSSH_KEY_DIAGNOSTIC '; stat -c '%U:%G %a' \"$ssh_home/.ssh\" \"$ssh_home/.ssh/authorized_keys\"; grep -c 'xkp5-platform' \"$ssh_home/.ssh/authorized_keys\""
                    + "; sudo -S -p '' rm -rf " + quote(remoteDir);
            String passwordInput = request.getPassword() + "\n" + request.getPassword() + "\n"
                    + request.getPassword() + "\n" + request.getPassword() + "\n";
            String output = exec(session, command, passwordInput);
            Map<String, Object> result = new LinkedHashMap<>(); result.put("success", true); result.put("message", "Agent 已远程部署并启动"); result.put("output", output); return result;
        } catch (Exception error) {
            Map<String, Object> result = new LinkedHashMap<>(); result.put("success", false); result.put("message", error.getMessage() == null ? "远程部署失败" : error.getMessage()); return result;
        } finally { if (session != null) session.disconnect(); }
    }

    private String exec(Session session, String command) throws Exception { return exec(session, command, null); }

    private String exec(Session session, String command, String stdin) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command); channel.setInputStream(stdin == null ? null : new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8))); channel.setErrStream(null);
        InputStream input = channel.getInputStream(); channel.connect(10000);
        byte[] buffer = new byte[8192]; StringBuilder output = new StringBuilder();
        while (!channel.isClosed()) { while (input.available() > 0) { int n = input.read(buffer); if (n > 0) output.append(new String(buffer, 0, n, StandardCharsets.UTF_8)); } Thread.sleep(100); }
        while (input.available() > 0) { int n = input.read(buffer); if (n > 0) output.append(new String(buffer, 0, n, StandardCharsets.UTF_8)); }
        int status = channel.getExitStatus(); channel.disconnect(); if (status != 0) throw new IllegalStateException("远程命令失败，退出码 " + status + "：" + output); return output.toString();
    }

    private void validate(RemoteAgentDeployRequest request) {
        if (request == null || request.getServerIp() == null || !request.getServerIp().matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}") || request.getUsername() == null || request.getUsername().trim().isEmpty() || request.getPassword() == null || request.getPassword().isEmpty() || request.getLabel() == null || request.getLabel().trim().isEmpty()) throw new IllegalArgumentException("服务器 IP、SSH 用户名、密码和备注名称不能为空");
        if (request.getSshPort() == null || request.getSshPort() < 1 || request.getSshPort() > 65535) throw new IllegalArgumentException("SSH 端口无效");
        if (request.getWorkspace() == null || request.getWorkspace().trim().isEmpty() || !request.getWorkspace().startsWith("/")) throw new IllegalArgumentException("工作目录必须是绝对路径");
    }
    private String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
}
