package com.match.licensing.config;

import com.match.licensing.crypto.CanonicalJson;
import com.match.licensing.crypto.LicenseSignatureVerifier;
import com.match.licensing.crypto.StrictJson;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(LicenseProperties.class)
public class LicenseConfiguration {
    @Bean
    public StrictJson strictJson() {
        return new StrictJson();
    }

    @Bean
    public CanonicalJson canonicalJson() {
        return new CanonicalJson();
    }

    @Bean
    public LicenseSignatureVerifier licenseSignatureVerifier(StrictJson strictJson, CanonicalJson canonicalJson) {
        return new LicenseSignatureVerifier(strictJson, canonicalJson);
    }

    @Bean
    public Clock licensingClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecureRandom licensingSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    @Profile("prod")
    public SmartInitializingSingleton productionLicenseKeyValidator(LicenseProperties properties) {
        return () -> {
            if (properties.productionKeys().isEmpty()) {
                throw new IllegalStateException(
                        "生产环境必须通过 XKP_LICENSE_PUBLIC_KEYS 配置至少一个授权公钥");
            }
        };
    }
}
