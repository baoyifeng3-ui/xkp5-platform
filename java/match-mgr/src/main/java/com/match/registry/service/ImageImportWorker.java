package com.match.registry.service;

import com.match.registry.persistence.ImageArtifactMapper;
import com.match.registry.persistence.ImageArtifactRecord;
import com.match.registry.persistence.ImageUploadMapper;
import com.match.registry.persistence.ImageUploadRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "xkp.registry.import", name = "enabled", havingValue = "true")
public class ImageImportWorker {
    private static final int BATCH_SIZE = 10;
    private static final int MAX_FAILURE_MESSAGE = 512;
    private static final Pattern IMMUTABLE_DIGEST = Pattern.compile("^sha256:[0-9a-f]{64}$");

    private final ImageArtifactMapper artifactMapper;
    private final ImageUploadMapper uploadMapper;
    private final RegistryImportTool importTool;
    private final DockerArchiveInspector archiveInspector;
    private final Path stagingRoot;
    private final Clock clock;
    private final long leaseSeconds;

    @Autowired
    public ImageImportWorker(ImageArtifactMapper artifactMapper, ImageUploadMapper uploadMapper,
                             RegistryImportTool importTool,
                             @Value("${xkp.registry.staging-dir:${REGISTRY_STAGING_ROOT:/data/registry-staging}}") String stagingRoot,
                             @Value("${xkp.registry.import.lease-seconds:900}") long leaseSeconds) {
        this(artifactMapper, uploadMapper, importTool, Paths.get(stagingRoot), Clock.systemUTC(),
                leaseSeconds);
    }

    public ImageImportWorker(ImageArtifactMapper artifactMapper, ImageUploadMapper uploadMapper,
                             RegistryImportTool importTool, Path stagingRoot, Clock clock) {
        this(artifactMapper, uploadMapper, importTool, stagingRoot, clock, 900);
    }

    ImageImportWorker(ImageArtifactMapper artifactMapper, ImageUploadMapper uploadMapper,
                      RegistryImportTool importTool, Path stagingRoot, Clock clock,
                      long leaseSeconds) {
        this.artifactMapper = artifactMapper;
        this.uploadMapper = uploadMapper;
        this.importTool = importTool;
        this.archiveInspector = new DockerArchiveInspector();
        this.stagingRoot = stagingRoot.toAbsolutePath().normalize();
        this.clock = clock;
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${xkp.registry.import.poll-delay-ms:5000}")
    public void poll() {
        List<ImageArtifactRecord> candidates = artifactMapper.selectImportCandidates(now(), BATCH_SIZE);
        for (ImageArtifactRecord candidate : candidates) {
            try {
                process(candidate.getArtifactId());
            } catch (RuntimeException ignored) {
                // One artifact cannot block later approved imports in the bounded batch.
            }
        }
    }

    public boolean process(String artifactId) {
        ImageArtifactRecord artifact = artifactMapper.selectById(artifactId);
        if (artifact == null || !"APPROVED".equals(artifact.getReviewState())) {
            return false;
        }
        if ("READY".equals(artifact.getImportState())
                && IMMUTABLE_DIGEST.matcher(value(artifact.getRegistryDigest())).matches()) {
            return true;
        }

        LocalDateTime now = now();
        String attemptToken = UUID.randomUUID().toString();
        if (artifactMapper.claimImport(artifactId, attemptToken, now,
                now.plusSeconds(leaseSeconds)) != 1) {
            return false;
        }

        try {
            ImageUploadRecord upload = uploadMapper.selectByArtifactId(artifactId);
            if (upload == null) {
                return fail(artifactId, attemptToken, "UPLOAD_NOT_FOUND",
                        "Completed upload metadata is missing");
            }
            Path archive = archive(upload.getUploadId());
            progress(artifactId, attemptToken, "VALIDATING", 10, 0, 0);
            int totalLayers = archiveInspector.countLayers(archive);
            progress(artifactId, attemptToken, "PREPARING", 20, 0, totalLayers);
            AtomicInteger copiedLayers = new AtomicInteger();
            AtomicLong lastHeartbeat = new AtomicLong(System.nanoTime());
            RegistryImportTool.ImportResult result = importTool.importArchive(
                    new RegistryImportTool.ImportRequest(archive, artifact.getImageRepository(),
                            artifact.getImageTag()), new RegistryImportTool.ProgressListener() {
                        @Override
                        public void onLayerCopy(int count) {
                            copiedLayers.set(count);
                            lastHeartbeat.set(System.nanoTime());
                            updateCopyProgress(artifactId, attemptToken, count, totalLayers);
                        }

                        @Override
                        public void onActivity() {
                            long previous = lastHeartbeat.get();
                            long current = System.nanoTime();
                            if (current - previous >= TimeUnit.SECONDS.toNanos(10)
                                    && lastHeartbeat.compareAndSet(previous, current)) {
                                updateCopyProgress(artifactId, attemptToken,
                                        copiedLayers.get(), totalLayers);
                            }
                        }
                    });
            String digest = result == null ? null : result.getDigest();
            if (!IMMUTABLE_DIGEST.matcher(value(digest)).matches()) {
                return fail(artifactId, attemptToken, "INVALID_REGISTRY_DIGEST",
                        "Importer did not return an immutable lowercase SHA-256 digest");
            }
            progress(artifactId, attemptToken, "VERIFYING_DIGEST", 95, totalLayers, totalLayers);
            if (artifactMapper.completeImport(artifactId, attemptToken, digest, now()) != 1) {
                return fail(artifactId, attemptToken, "DIGEST_PERSIST_FAILED",
                        "Registry digest could not be persisted");
            }
            return true;
        } catch (RegistryImportTool.ImportException e) {
            return fail(artifactId, attemptToken, e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            return fail(artifactId, attemptToken, "IMPORT_FAILED", e.getMessage());
        }
    }

    private void progress(String artifactId, String attemptToken, String stage, int progress,
                          int completedLayers, int totalLayers) {
        artifactMapper.updateImportProgress(artifactId, attemptToken, stage, progress,
                completedLayers, totalLayers, now());
    }

    private int copyingProgress(int copiedLayers, int totalLayers) {
        if (totalLayers <= 0) return 20;
        return Math.min(90, 20 + Math.min(copiedLayers, totalLayers) * 70 / totalLayers);
    }

    private void updateCopyProgress(String artifactId, String attemptToken, int copiedLayers,
                                    int totalLayers) {
        progress(artifactId, attemptToken, "COPYING_LAYERS",
                copyingProgress(copiedLayers, totalLayers),
                Math.min(copiedLayers, totalLayers), totalLayers);
    }

    private boolean fail(String artifactId, String attemptToken, String code, String message) {
        artifactMapper.failImport(artifactId, attemptToken, code, bounded(message), now());
        return false;
    }

    private Path archive(String uploadId) {
        if (uploadId == null || uploadId.isEmpty()) {
            throw new RegistryImportTool.ImportException("ARCHIVE_NOT_FOUND", "Upload ID is missing");
        }
        Path result = stagingRoot.resolve(uploadId).resolve("archive").normalize();
        if (!result.startsWith(stagingRoot)) {
            throw new RegistryImportTool.ImportException("ARCHIVE_NOT_FOUND", "Invalid staging path");
        }
        return result;
    }

    private String bounded(String message) {
        String value = message == null || message.trim().isEmpty() ? "Registry import failed" : message.trim();
        return value.length() <= MAX_FAILURE_MESSAGE ? value : value.substring(0, MAX_FAILURE_MESSAGE);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
