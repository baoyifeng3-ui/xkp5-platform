package com.match.registry.service;

import com.match.registry.dto.ImageReviewRequest;
import com.match.registry.dto.ImageUploadChunkView;
import com.match.registry.dto.ImageUploadCreateRequest;
import com.match.registry.persistence.ImageArtifactMapper;
import com.match.registry.persistence.ImageArtifactRecord;
import com.match.registry.persistence.ImageUploadChunkMapper;
import com.match.registry.persistence.ImageUploadChunkRecord;
import com.match.registry.persistence.ImageUploadMapper;
import com.match.registry.persistence.ImageUploadRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

@Service
public class ImageUploadService {
    public static final String UPLOADING = "UPLOADING";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";
    public static final String CODE_AUTHORIZATION = "AUTHORIZATION_REQUIRED";
    public static final String CODE_SIZE = "UPLOAD_SIZE_LIMIT";
    public static final String CODE_STAGING = "STAGING_CAPACITY_LIMIT";
    public static final String CODE_CHECKSUM = "CHECKSUM_MISMATCH";
    public static final String CODE_ARCHIVE = "INVALID_DOCKER_ARCHIVE";
    public static final String CODE_CONFLICT = "CHUNK_CHECKSUM_CONFLICT";
    public static final String CODE_STATE = "INVALID_UPLOAD_STATE";

    private final ImageUploadMapper uploadMapper;
    private final ImageUploadChunkMapper chunkMapper;
    private final ImageArtifactMapper artifactMapper;
    private final Path stagingRoot;
    private final long maxFileBytes;
    private final long maxStagingBytes;
    private final Clock clock;

    public ImageUploadService(ImageUploadMapper uploadMapper, ImageUploadChunkMapper chunkMapper,
                              ImageArtifactMapper artifactMapper, Path stagingRoot,
                              long maxFileBytes, long maxStagingBytes, Clock clock) {
        this.uploadMapper = uploadMapper;
        this.chunkMapper = chunkMapper;
        this.artifactMapper = artifactMapper;
        this.stagingRoot = stagingRoot;
        this.maxFileBytes = maxFileBytes;
        this.maxStagingBytes = maxStagingBytes;
        this.clock = clock;
    }

    public ImageUploadService(ImageUploadMapper uploadMapper, ImageUploadChunkMapper chunkMapper,
                              ImageArtifactMapper artifactMapper,
                              @Value("${xkp.registry.staging-dir:${java.io.tmpdir}/xkp-registry-staging}") String staging,
                              @Value("${xkp.registry.max-file-bytes:21474836480}") long maxFileBytes,
                              @Value("${xkp.registry.max-staging-bytes:107374182400}") long maxStagingBytes) {
        this(uploadMapper, chunkMapper, artifactMapper, java.nio.file.Paths.get(staging),
                maxFileBytes, maxStagingBytes, Clock.systemUTC());
    }

    private void requireSuperAdmin(String role) {
        if (!"SUPER_ADMIN".equals(role)) throw error(CODE_AUTHORIZATION, "SUPER_ADMIN required");
    }

    @Transactional
    public ImageUploadRecord create(String role, ImageUploadCreateRequest request, int actorId) {
        requireSuperAdmin(role);
        if (request == null || request.getTotalSize() == null || request.getTotalSize() < 0
                || request.getTotalSize() > maxFileBytes) throw error(CODE_SIZE, "file exceeds configured limit");
        if (request.getChunkSize() == null || request.getChunkSize() <= 0) throw error(CODE_SIZE, "invalid chunk size");
        String uploadId = UUID.randomUUID().toString();
        String artifactId = request.getArtifactId() == null ? UUID.randomUUID().toString() : request.getArtifactId();
        LocalDateTime now = now();
        ImageArtifactRecord artifact = new ImageArtifactRecord();
        artifact.setArtifactId(artifactId); artifact.setGroupId(request.getGroupId()); artifact.setComponentType(request.getComponentType());
        artifact.setOriginalFilename(request.getOriginalFilename()); artifact.setSizeBytes(request.getTotalSize()); artifact.setSha256(request.getExpectedSha256());
        artifact.setVersion(request.getVersion()); artifact.setImageRepository(request.getImageRepository()); artifact.setImageTag(request.getImageTag());
        artifact.setReviewState("UPLOADING"); artifact.setImportState("NOT_IMPORTED"); artifact.setCreatedBy(actorId); artifact.setCreatedAt(now); artifact.setUpdatedAt(now);
        if (artifactMapper.selectById(artifactId) != null) throw error(CODE_STATE, "artifact already exists");
        artifactMapper.insert(artifact);
        ImageUploadRecord upload = new ImageUploadRecord(); upload.setUploadId(uploadId); upload.setArtifactId(artifactId);
        upload.setOriginalFilename(request.getOriginalFilename()); upload.setTotalSize(request.getTotalSize()); upload.setChunkSize(request.getChunkSize());
        upload.setTotalChunks((int) ((request.getTotalSize() + request.getChunkSize() - 1) / request.getChunkSize())); upload.setReceivedBytes(0L);
        upload.setExpectedSha256(normalize(request.getExpectedSha256())); upload.setState(UPLOADING); upload.setExpiresAt(now.plusHours(24)); upload.setCreatedAt(now); upload.setUpdatedAt(now);
        uploadMapper.insert(upload);
        try { Files.createDirectories(stagingRoot.resolve(uploadId)); } catch (IOException e) { throw error(CODE_STAGING, e.getMessage()); }
        return upload;
    }

    @Transactional
    public ImageUploadChunkView putChunk(String role, String uploadId, int index, MultipartFile file, String checksum) {
        requireSuperAdmin(role); ImageUploadRecord upload = locked(uploadId); expireIfNeeded(upload);
        if (!UPLOADING.equals(upload.getState())) throw error(CODE_STATE, "upload is not writable");
        if (index < 0 || index >= upload.getTotalChunks()) throw error(CODE_STATE, "invalid chunk index");
        try (InputStream in = file.getInputStream()) { return putChunkInternal(uploadId, index, in, file.getSize(), checksum); }
        catch (IOException e) { throw error(CODE_STAGING, e.getMessage()); }
    }

    @Transactional
    public ImageUploadChunkView putChunk(String role, String uploadId, int index, InputStream input, long size, String checksum) {
        requireSuperAdmin(role); return putChunkInternal(uploadId, index, input, size, checksum);
    }

    private ImageUploadChunkView putChunkInternal(String uploadId, int index, InputStream in, long size, String checksum) {
        ImageUploadRecord upload = locked(uploadId); expireIfNeeded(upload);
        if (!UPLOADING.equals(upload.getState())) throw error(CODE_STATE, "upload is not writable");
        if (index < 0 || index >= upload.getTotalChunks() || size < 0 || size > upload.getChunkSize()) throw error(CODE_SIZE, "invalid chunk");
        String normalized = normalize(checksum); Path dir = stagingRoot.resolve(uploadId); Path target = dir.resolve(index + ".part");
        ImageUploadChunkRecord existing = chunkMapper.selectForUpdate(uploadId, index);
        if (existing != null) {
            if (existing.getChunkSha256().equals(normalized) && Files.exists(target)) return view(upload, existing);
            throw error(CODE_CONFLICT, "chunk checksum conflicts with existing chunk");
        }
        if (stagedBytes() + size > maxStagingBytes) throw error(CODE_STAGING, "staging capacity exceeded");
        try {
            Files.createDirectories(dir); Path tmp = dir.resolve(index + ".part.tmp"); MessageDigest md = MessageDigest.getInstance("SHA-256");
            long copied = 0; byte[] buf = new byte[8192];
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                int n; while ((n = in.read(buf)) != -1) { copied += n; if (copied > size) throw error(CODE_SIZE, "chunk size mismatch"); md.update(buf, 0, n); out.write(buf, 0, n); }
            }
            if (copied != size) throw error(CODE_SIZE, "chunk size mismatch"); String actual = hex(md.digest());
            if (normalized == null || !actual.equals(normalized)) throw error(CODE_CHECKSUM, "chunk checksum mismatch");
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            ImageUploadChunkRecord record = new ImageUploadChunkRecord(); record.setUploadId(uploadId); record.setChunkIndex(index); record.setChunkSize((int) size); record.setChunkSha256(actual); record.setStoredBytes(size); record.setCreatedAt(now()); chunkMapper.insertOrRetry(uploadId, index, (int) size, actual, size, record.getCreatedAt());
            upload.setReceivedBytes(upload.getReceivedBytes() + size); upload.setUpdatedAt(now()); uploadMapper.updateById(upload); return view(upload, record);
        } catch (UploadException e) { throw e; } catch (Exception e) { throw error(CODE_STAGING, e.getMessage()); }
    }

    private long stagedBytes() {
        if (!Files.exists(stagingRoot)) return 0;
        try { return Files.walk(stagingRoot).filter(Files::isRegularFile).mapToLong(p -> { try { return Files.size(p); } catch (IOException e) { return 0; } }).sum(); }
        catch (IOException e) { return maxStagingBytes; }
    }

    private ImageUploadChunkView view(ImageUploadRecord upload, ImageUploadChunkRecord chunk) { ImageUploadChunkView v = new ImageUploadChunkView(); v.setUploadId(upload.getUploadId()); v.setChunkIndex(chunk.getChunkIndex()); v.setChunkSize(chunk.getChunkSize()); v.setChunkSha256(chunk.getChunkSha256()); v.setReceivedBytes(upload.getReceivedBytes()); v.setTotalChunks(upload.getTotalChunks()); v.setState(upload.getState()); return v; }

    @Transactional
    public ImageUploadRecord complete(String role, String uploadId) {
        requireSuperAdmin(role); ImageUploadRecord upload = locked(uploadId); expireIfNeeded(upload);
        if (PENDING_REVIEW.equals(upload.getState())) return upload;
        if (!UPLOADING.equals(upload.getState())) throw error(CODE_STATE, "upload cannot complete");
        List<ImageUploadChunkRecord> chunks = chunkMapper.selectByUpload(uploadId); if (chunks.size() != upload.getTotalChunks()) throw error(CODE_STATE, "missing chunks");
        Path assembled = stagingRoot.resolve(uploadId).resolve("archive");
        if (stagedBytes() + upload.getTotalSize() > maxStagingBytes) throw error(CODE_STAGING, "staging capacity exceeded");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(assembled, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (int i = 0; i < upload.getTotalChunks(); i++) { Path p = stagingRoot.resolve(uploadId).resolve(i + ".part"); if (!Files.exists(p)) throw error(CODE_STATE, "missing chunk"); Files.copy(p, out); }
        } catch (UploadException e) { throw e; } catch (IOException e) { throw error(CODE_STAGING, e.getMessage()); }
        String sha = digestFile(assembled); if (upload.getExpectedSha256() != null && !upload.getExpectedSha256().equals(sha)) throw error(CODE_CHECKSUM, "archive checksum mismatch");
        if (!isDockerSave(assembled)) throw error(CODE_ARCHIVE, "Docker save archive is invalid");
        upload.setFinalSha256(sha); upload.setState(PENDING_REVIEW); upload.setCompletedAt(now()); upload.setUpdatedAt(now()); uploadMapper.updateById(upload);
        ImageArtifactRecord artifact = artifactMapper.selectById(upload.getArtifactId()); if (artifact != null) { artifact.setSha256(sha); artifact.setReviewState(PENDING_REVIEW); artifact.setUpdatedAt(now()); artifactMapper.updateById(artifact); }
        return upload;
    }

    private String digestFile(Path file) { try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) { MessageDigest md = MessageDigest.getInstance("SHA-256"); byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) md.update(b, 0, n); return hex(md.digest()); } catch (Exception e) { throw error(CODE_CHECKSUM, e.getMessage()); } }
    private boolean isDockerSave(Path file) { try (InputStream raw = new BufferedInputStream(Files.newInputStream(file))) { raw.mark(2); int a = raw.read(), b = raw.read(); raw.reset(); InputStream in = (a == 0x1f && b == 0x8b) ? new GZIPInputStream(raw) : raw; byte[] header = new byte[512]; boolean manifest = false; while (readFully(in, header)) { String name = new String(header, 0, 100, "UTF-8").trim(); long size = 0; for (int i = 124; i < 136 && header[i] >= '0' && header[i] <= '7'; i++) size = size * 8 + header[i] - '0'; if (name.equals("manifest.json")) manifest = true; long skip = (size + 511) & ~511L; while (skip > 0) { long s = in.skip(skip); if (s <= 0) break; skip -= s; } } return manifest; } catch (Exception e) { return false; } }
    private boolean readFully(InputStream in, byte[] b) throws IOException { int off = 0, n; while (off < b.length && (n = in.read(b, off, b.length - off)) != -1) off += n; return off == b.length; }

    @Transactional public ImageUploadRecord cancel(String role, String uploadId) { requireSuperAdmin(role); ImageUploadRecord x = locked(uploadId); if (UPLOADING.equals(x.getState())) { x.setState(CANCELLED); x.setUpdatedAt(now()); uploadMapper.updateById(x); } return x; }
    @Transactional public ImageArtifactRecord review(String role, String artifactId, ImageReviewRequest request, int reviewerId) { requireSuperAdmin(role); ImageArtifactRecord a = artifactMapper.selectById(artifactId); if (a == null) throw error(CODE_STATE, "artifact not found"); String d = request == null ? null : request.getDecision(); if (APPROVED.equals(a.getReviewState()) && APPROVED.equals(d) || REJECTED.equals(a.getReviewState()) && REJECTED.equals(d)) return a; if (!PENDING_REVIEW.equals(a.getReviewState())) throw error(CODE_STATE, "artifact is not pending review"); if (!APPROVED.equals(d) && !REJECTED.equals(d)) throw error(CODE_STATE, "invalid review decision"); a.setReviewState(d); a.setReviewedBy(reviewerId); a.setReviewedAt(now()); a.setFailureMessage(request.getReason()); a.setUpdatedAt(now()); artifactMapper.updateById(a); return a; }
    public ImageUploadRecord status(String role, String uploadId) { if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) throw error(CODE_AUTHORIZATION, "admin required"); return uploadMapper.selectById(uploadId); }

    private ImageUploadRecord locked(String id) { ImageUploadRecord x = uploadMapper.selectForUpdate(id); if (x == null) throw error(CODE_STATE, "upload not found"); return x; }
    private void expireIfNeeded(ImageUploadRecord x) { if (x.getExpiresAt() != null && x.getExpiresAt().isBefore(now()) && UPLOADING.equals(x.getState())) { x.setState(EXPIRED); uploadMapper.updateById(x); throw error(CODE_STATE, "upload expired"); } }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private String normalize(String s) { return s == null ? null : s.toLowerCase(Locale.ROOT); }
    private IllegalArgumentException error(String code, String msg) { return new UploadException(code, msg); }
    private String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
    public static class UploadException extends IllegalArgumentException { private final String code; public UploadException(String code, String message) { super(message); this.code = code; } public String getCode() { return code; } }
}
