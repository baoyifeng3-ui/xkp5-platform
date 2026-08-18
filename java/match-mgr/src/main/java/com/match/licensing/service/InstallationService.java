package com.match.licensing.service;

import com.match.licensing.persistence.PlatformInstallation;
import com.match.licensing.persistence.PlatformInstallationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class InstallationService {
    private static final String PRIMARY = "PRIMARY";

    private final PlatformInstallationMapper mapper;
    private final Clock clock;

    public InstallationService(PlatformInstallationMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public String installationId() {
        PlatformInstallation existing = mapper.selectById(PRIMARY);
        if (existing != null) {
            return existing.getInstallationId();
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        PlatformInstallation created = new PlatformInstallation();
        created.setInstallationKey(PRIMARY);
        created.setInstallationId(UUID.randomUUID().toString());
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        try {
            mapper.insert(created);
            return created.getInstallationId();
        } catch (DuplicateKeyException concurrentCreation) {
            PlatformInstallation concurrent = mapper.selectById(PRIMARY);
            if (concurrent != null) {
                return concurrent.getInstallationId();
            }
            throw concurrentCreation;
        }
    }
}
