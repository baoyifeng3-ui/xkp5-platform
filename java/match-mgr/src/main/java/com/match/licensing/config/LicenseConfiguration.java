package com.match.licensing.config;

import com.match.licensing.crypto.CanonicalJson;
import com.match.licensing.crypto.LicenseSignatureVerifier;
import com.match.licensing.crypto.StrictJson;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
