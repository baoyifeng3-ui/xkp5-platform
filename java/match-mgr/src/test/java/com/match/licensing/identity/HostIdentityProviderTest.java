package com.match.licensing.identity;

import com.match.licensing.crypto.StrictJson;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HostIdentityProviderTest {
    @Test
    public void productionRequiresTwoUbuntuIdentifiers() throws Exception {
        Path identityFile = identityFile("{\"machineId\":\"machine-one\"}");
        UbuntuHostIdentityProvider provider = new UbuntuHostIdentityProvider(
                identityFile, "Linux", new StrictJson());

        try {
            provider.load();
            fail("production identity must contain at least two identifiers");
        } catch (IllegalStateException expected) {
            assertEquals("生产环境至少需要两个宿主机标识", expected.getMessage());
        }
    }

    @Test
    public void productionRejectsNonLinuxHosts() throws Exception {
        Path identityFile = identityFile("{\"machineId\":\"machine-one\",\"productUuid\":\"UUID-ONE\"}");
        UbuntuHostIdentityProvider provider = new UbuntuHostIdentityProvider(
                identityFile, "Windows 11", new StrictJson());

        try {
            provider.load();
            fail("production identity must be generated on Linux");
        } catch (IllegalStateException expected) {
            assertEquals("正式授权只能绑定 Ubuntu 管理服务器", expected.getMessage());
        }
    }

    @Test
    public void productionFingerprintIsStableAcrossFieldOrderAndWhitespace() throws Exception {
        UbuntuHostIdentityProvider firstProvider = new UbuntuHostIdentityProvider(
                identityFile("{\"machineId\":\" machine-one \",\"productUuid\":\"ABC-123\"}"),
                "Linux", new StrictJson());
        UbuntuHostIdentityProvider secondProvider = new UbuntuHostIdentityProvider(
                identityFile("{\"productUuid\":\"abc-123\",\"machineId\":\"machine-one\"}"),
                "linux", new StrictJson());

        HostIdentity first = firstProvider.load();
        HostIdentity second = secondProvider.load();

        assertEquals("PRODUCTION", first.getEnvironment());
        assertEquals(first.getFingerprint(), second.getFingerprint());
        assertTrue(first.getFingerprint().startsWith("sha256:"));
        assertNotEquals("machine-one", first.getFingerprint());
    }

    @Test
    public void developmentFingerprintIsStableAndMarkedDevelopment() {
        DevelopmentHostIdentityProvider provider = new DevelopmentHostIdentityProvider(" windows-docker-development ");

        HostIdentity first = provider.load();

        assertEquals("DEVELOPMENT", first.getEnvironment());
        assertEquals(first.getFingerprint(), provider.load().getFingerprint());
        assertTrue(first.getFingerprint().startsWith("sha256:"));
    }

    @Test(expected = IllegalStateException.class)
    public void developmentIdentityCannotBeBlank() {
        new DevelopmentHostIdentityProvider("  ").load();
    }

    private Path identityFile(String content) throws Exception {
        Path path = Files.createTempFile("xkp-host-identity", ".json");
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        path.toFile().deleteOnExit();
        return path;
    }
}
