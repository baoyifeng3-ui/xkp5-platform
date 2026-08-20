package com.match.licensing.identity;

import com.match.licensing.crypto.StrictJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Paths;

@Configuration
public class HostIdentityConfiguration {
    @Bean
    @Profile("prod")
    public HostIdentityProvider productionHostIdentityProvider(
            @Value("${xkp.licensing.host-identity-file}") String identityFile,
            StrictJson strictJson) {
        return new UbuntuHostIdentityProvider(Paths.get(identityFile), System.getProperty("os.name"), strictJson);
    }

    @Bean
    @Profile("local")
    public HostIdentityProvider developmentHostIdentityProvider(
            @Value("${xkp.licensing.development-identity}") String developmentIdentity) {
        return new DevelopmentHostIdentityProvider(developmentIdentity);
    }
}
