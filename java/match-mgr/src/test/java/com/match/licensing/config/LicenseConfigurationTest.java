package com.match.licensing.config;

import org.junit.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LicenseConfigurationTest {
    @Test
    public void productionStartupRequiresAtLeastOnePublicKey() {
        LicenseProperties properties = new LicenseProperties();
        SmartInitializingSingleton validator = new LicenseConfiguration().productionLicenseKeyValidator(properties);

        try {
            validator.afterSingletonsInstantiated();
            fail("production startup accepted an empty public key configuration");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("XKP_LICENSE_PUBLIC_KEYS"));
        }
    }

    @Test
    public void productionStartupAcceptsAConfiguredPublicKey() {
        LicenseProperties properties = new LicenseProperties();
        properties.setPublicKeys("production-2026-01=public-key-fixture");

        new LicenseConfiguration().productionLicenseKeyValidator(properties).afterSingletonsInstantiated();
    }
}
