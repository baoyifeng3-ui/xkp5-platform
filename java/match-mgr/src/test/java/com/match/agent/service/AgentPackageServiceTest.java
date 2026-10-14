package com.match.agent.service;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AgentPackageServiceTest {
    @Test public void installerAuthorizesBothDeploymentUserAndRoot() throws Exception {
        AgentPackageService service = new AgentPackageService(null, ".", ".", "https://localhost", ".", ".");
        java.lang.reflect.Method wrapper = AgentPackageService.class.getDeclaredMethod(
                "wrapper", String.class, String.class, String.class);
        wrapper.setAccessible(true);
        String script = (String) wrapper.invoke(service, "test-token", "test-agent", "/srv/xkp");
        assertTrue(script.contains("\"$ssh_home/.ssh/authorized_keys\""));
        assertTrue(script.contains("/root/.ssh/authorized_keys"));
        assertTrue(script.contains("grep -qxFf deploy/management-ssh.pub /root/.ssh/authorized_keys"));
    }

    @Test public void packagedShellScriptUsesUnixLineEndings() throws Exception {
        Path script = Files.createTempFile("xkp-agent-install-", ".sh");
        try {
            Files.writeString(script, "#!/usr/bin/env bash\r\nset -Eeuo pipefail\r\necho ready\r\n", StandardCharsets.UTF_8);
            ByteArrayOutputStream archive = new ByteArrayOutputStream();
            new AgentPackageService(null, ".", ".", "https://localhost", ".", ".")
                    .addShellScript(archive, "deploy/install.sh", script);

            byte[] bytes = archive.toByteArray();
            String content = new String(bytes, 512, "#!/usr/bin/env bash\nset -Eeuo pipefail\necho ready\n".length(), StandardCharsets.UTF_8);
            assertEquals("#!/usr/bin/env bash\nset -Eeuo pipefail\necho ready\n", content);
            assertFalse(content.contains("\r"));
        } finally {
            Files.deleteIfExists(script);
        }
    }
}
