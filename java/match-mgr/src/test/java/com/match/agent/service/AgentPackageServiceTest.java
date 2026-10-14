package com.match.agent.service;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AgentPackageServiceTest {
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
