package com.match.registry.service;

import com.match.registry.persistence.ImageArtifactMapper;
import com.match.registry.persistence.ImageArtifactRecord;
import com.match.registry.persistence.ImageUploadMapper;
import com.match.registry.persistence.ImageUploadRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.regex.Pattern;

@Component
public class ImageImportWorker {
    private static final int BATCH_SIZE = 10;
    private static final int MAX_FAILURE_MESSAGE = 512;
    private static final Pattern IMMUTABLE_DIGEST = Pattern.compile("^sha256:[0-9a-f]{64}$");

    private final ImageArtifactMapper artifactMapper;
    private final ImageUploadMapper uploadMapper;
    private final RegistryImportTool importTool;
    private final Path stagingRoot;
    private final Clock clock;

    @Autowired
    public ImageImportWorker(ImageArtifactMapper artifactMapper, ImageUploadMapper uploadMapper,
                             RegistryImportTool importTool,
                             @Value("${xkp.registry.staging-dir:${REGISTRY_STAGING_ROOT:/data/registry-staging}}") String stagingRoot) {
        this(artifactMapper, uploadMapper, importTool, Paths.get(stagingRoot), Clock.systemUTC());
    }

    public ImageImportWorker(ImageArtifactMapper artifactMapper, ImageUploadMapper uploadMapper,
                             RegistryImportTool importTool, Path stagingRoot, Clock clock) {
        this.artifactMapper = artifactMapper;
        this.uploadMapper = uploadMapper;
        this.importTool = importTool;
        this.stagingRoot = stagingRoot.toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${xkp.registry.import.poll-delay-ms:5000}")
    public void poll() {
        List<ImageArtifactRecord> candidates = artifactMapper.selectImportCandidates(BATCH_SIZE);
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
            ImageUploadRecord completedUpload = uploadMapper.selectByArtifactId(artifactId);
            if (completedUpload != null) {
                deleteImportedArchive(archive(completedUpload.getUploadId()));
            }
            return true;
        }

        LocalDateTime now = now();
        if (artifactMapper.claimImport(artifactId, now) != 1) {
            return false;
        }

        try {
            ImageUploadRecord upload = uploadMapper.selectByArtifactId(artifactId);
            if (upload == null) {
                return fail(artifactId, "UPLOAD_NOT_FOUND", "Completed upload metadata is missing");
            }
            Path archive = archive(upload.getUploadId());
            RegistryImportTool.ImportResult result = importTool.importArchive(
                    new RegistryImportTool.ImportRequest(archive, artifact.getImageRepository(),
                            artifact.getImageTag()));
            String digest = result == null ? null : result.getDigest();
            if (!IMMUTABLE_DIGEST.matcher(value(digest)).matches()) {
                return fail(artifactId, "INVALID_REGISTRY_DIGEST",
                        "Importer did not return an immutable lowercase SHA-256 digest");
            }
            if (artifactMapper.completeImport(artifactId, digest, now()) != 1) {
                return fail(artifactId, "DIGEST_PERSIST_FAILED",
                        "Registry digest could not be persisted");
            }
            deleteImportedArchive(archive);
            return true;
        } catch (RegistryImportTool.ImportException e) {
            return fail(artifactId, e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            return fail(artifactId, "IMPORT_FAILED", e.getMessage());
        }
    }

    private boolean fail(String artifactId, String code, String message) {
        artifactMapper.failImport(artifactId, code, bounded(message), now());
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

    private void deleteImportedArchive(Path archive) {
        try {
            Files.deleteIfExists(archive);
        } catch (IOException ignored) {
            // Digest is durable; periodic staging cleanup may retry this best-effort deletion.
        }
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
