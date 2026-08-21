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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

@Service
public class ImageUploadService {
    private static final int TAR_BLOCK = 512;
    private static final long MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_CHUNKS = 100000;
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
        long chunkCount = (request.getTotalSize() + request.getChunkSize() - 1) / request.getChunkSize();
        if (chunkCount < 1 || chunkCount > MAX_CHUNKS || chunkCount > Integer.MAX_VALUE) throw error(CODE_SIZE, "too many chunks");
        requireText(request.getGroupId(), "groupId"); requireText(request.getComponentType(), "componentType");
        requireText(request.getOriginalFilename(), "originalFilename"); requireText(request.getVersion(), "version");
        if (request.getOriginalFilename().contains("/") || request.getOriginalFilename().contains("\\") || request.getOriginalFilename().contains("..")) throw error(CODE_STATE, "invalid originalFilename");
        if (!validSha256(request.getExpectedSha256())) throw error(CODE_CHECKSUM, "expectedSha256 must be lowercase SHA-256");
        String uploadId = UUID.randomUUID().toString();
        String artifactId = request.getArtifactId() == null ? UUID.randomUUID().toString() : request.getArtifactId();
        LocalDateTime now = now();
        ImageArtifactRecord artifact = new ImageArtifactRecord();
        artifact.setArtifactId(artifactId); artifact.setGroupId(request.getGroupId()); artifact.setComponentType(request.getComponentType());
        artifact.setOriginalFilename(request.getOriginalFilename()); artifact.setSizeBytes(request.getTotalSize()); artifact.setSha256(request.getExpectedSha256());
        artifact.setVersion(request.getVersion()); artifact.setImageRepository(request.getImageRepository()); artifact.setImageTag(request.getImageTag());
        artifact.setReviewState("UPLOADING"); artifact.setImportState("NOT_IMPORTED"); artifact.setCreatedBy(actorId); artifact.setCreatedAt(now); artifact.setUpdatedAt(now);
        if (artifactMapper.selectById(artifactId) != null) throw error(CODE_STATE, "artifact already exists");
        try { artifactMapper.insert(artifact); } catch (DataIntegrityViolationException e) { throw error(CODE_STATE, "artifact checksum or id already exists"); }
        ImageUploadRecord upload = new ImageUploadRecord(); upload.setUploadId(uploadId); upload.setArtifactId(artifactId);
        upload.setOriginalFilename(request.getOriginalFilename()); upload.setTotalSize(request.getTotalSize()); upload.setChunkSize(request.getChunkSize());
        upload.setTotalChunks((int) chunkCount); upload.setReceivedBytes(0L);
        upload.setExpectedSha256(normalize(request.getExpectedSha256())); upload.setState(UPLOADING); upload.setExpiresAt(now.plusHours(24)); upload.setCreatedAt(now); upload.setUpdatedAt(now);
        uploadMapper.insert(upload);
        try { Files.createDirectories(stagingRoot.resolve(uploadId)); } catch (IOException e) { throw error(CODE_STAGING, e.getMessage()); }
        return upload;
    }

    @Transactional(noRollbackFor = UploadException.class)
    public ImageUploadChunkView putChunk(String role, String uploadId, int index, MultipartFile file, String checksum) {
        requireSuperAdmin(role); ImageUploadRecord upload = locked(uploadId); expireIfNeeded(upload);
        if (!UPLOADING.equals(upload.getState())) throw error(CODE_STATE, "upload is not writable");
        if (index < 0 || index >= upload.getTotalChunks()) throw error(CODE_STATE, "invalid chunk index");
        try (InputStream in = file.getInputStream()) { return putChunkInternal(uploadId, index, in, file.getSize(), checksum); }
        catch (IOException e) { throw error(CODE_STAGING, e.getMessage()); }
    }

    @Transactional(noRollbackFor = UploadException.class)
    public ImageUploadChunkView putChunk(String role, String uploadId, int index, InputStream input, long size, String checksum) {
        requireSuperAdmin(role); return putChunkInternal(uploadId, index, input, size, checksum);
    }

    private ImageUploadChunkView putChunkInternal(String uploadId, int index, InputStream in, long size, String checksum) {
        ImageUploadRecord upload = locked(uploadId); expireIfNeeded(upload);
        if (!UPLOADING.equals(upload.getState())) throw error(CODE_STATE, "upload is not writable");
        long expectedSize = index == upload.getTotalChunks() - 1
                ? upload.getTotalSize() - (long) index * upload.getChunkSize() : upload.getChunkSize();
        if (index < 0 || index >= upload.getTotalChunks() || size != expectedSize) throw error(CODE_SIZE, "invalid chunk size");
        String normalized = normalize(checksum); Path dir = stagingRoot.resolve(uploadId); Path target = dir.resolve(index + ".part");
        ImageUploadChunkRecord existing = chunkMapper.selectForUpdate(uploadId, index);
        if (existing != null) {
            if (existing.getChunkSha256().equals(normalized) && Files.exists(target)) return view(upload, existing);
            throw error(CODE_CONFLICT, "chunk checksum conflicts with existing chunk");
        }
        Path reservation = dir.resolve(index + ".reserve"); Path tmp = reservation; reserveCapacity(reservation, size);
        try {
            Files.createDirectories(dir); MessageDigest md = MessageDigest.getInstance("SHA-256");
            long copied = 0; byte[] buf = new byte[8192];
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                int n; while ((n = in.read(buf)) != -1) { copied += n; if (copied > size) throw error(CODE_SIZE, "chunk size mismatch"); md.update(buf, 0, n); out.write(buf, 0, n); }
            }
            if (copied != size) throw error(CODE_SIZE, "chunk size mismatch"); String actual = hex(md.digest());
            if (normalized == null || !actual.equals(normalized)) throw error(CODE_CHECKSUM, "chunk checksum mismatch");
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            ImageUploadChunkRecord record = new ImageUploadChunkRecord(); record.setUploadId(uploadId); record.setChunkIndex(index); record.setChunkSize((int) size); record.setChunkSha256(actual); record.setStoredBytes(size); record.setCreatedAt(now()); chunkMapper.insertOrRetry(uploadId, index, (int) size, actual, size, record.getCreatedAt());
            upload.setReceivedBytes(upload.getReceivedBytes() + size); upload.setUpdatedAt(now()); uploadMapper.updateById(upload); return view(upload, record);
        } catch (UploadException e) { deleteQuietly(tmp); throw e; } catch (Exception e) { deleteQuietly(tmp); throw error(CODE_STAGING, e.getMessage()); } finally { deleteQuietly(reservation); }
    }

    private long stagedBytes() {
        if (!Files.exists(stagingRoot)) return 0;
        try (Stream<Path> paths = Files.walk(stagingRoot)) { return paths.filter(Files::isRegularFile).mapToLong(p -> { try { return Files.size(p); } catch (IOException e) { return 0; } }).sum(); }
        catch (IOException e) { return maxStagingBytes; }
    }

    private void reserveCapacity(Path reservation, long bytes) {
        try {
            Files.createDirectories(stagingRoot); Files.createDirectories(reservation.getParent());
            Path lockPath = stagingRoot.resolve(".quota.lock");
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                if (stagedBytes() + bytes > maxStagingBytes) throw error(CODE_STAGING, "staging capacity exceeded");
                try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(reservation.toFile(), "rw")) { file.setLength(bytes); }
            }
        } catch (UploadException e) { throw e; } catch (IOException e) { throw error(CODE_STAGING, e.getMessage()); }
    }

    private ImageUploadChunkView view(ImageUploadRecord upload, ImageUploadChunkRecord chunk) { ImageUploadChunkView v = new ImageUploadChunkView(); v.setUploadId(upload.getUploadId()); v.setChunkIndex(chunk.getChunkIndex()); v.setChunkSize(chunk.getChunkSize()); v.setChunkSha256(chunk.getChunkSha256()); v.setReceivedBytes(upload.getReceivedBytes()); v.setTotalChunks(upload.getTotalChunks()); v.setState(upload.getState()); return v; }

    @Transactional(noRollbackFor = UploadException.class)
    public ImageUploadRecord complete(String role, String uploadId) {
        requireSuperAdmin(role); ImageUploadRecord upload = locked(uploadId); expireIfNeeded(upload);
        if (PENDING_REVIEW.equals(upload.getState())) return upload;
        if (!UPLOADING.equals(upload.getState())) throw error(CODE_STATE, "upload cannot complete");
        List<ImageUploadChunkRecord> chunks = chunkMapper.selectByUpload(uploadId); if (chunks.size() != upload.getTotalChunks() || upload.getReceivedBytes().longValue() != upload.getTotalSize().longValue()) throw error(CODE_STATE, "missing or inconsistent chunks");
        Path assembled = stagingRoot.resolve(uploadId).resolve("archive");
        Path reservation = stagingRoot.resolve(uploadId).resolve("archive.reserve"); reserveCapacity(reservation, upload.getTotalSize());
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(assembled, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                for (int i = 0; i < upload.getTotalChunks(); i++) { Path p = stagingRoot.resolve(uploadId).resolve(i + ".part"); if (!Files.exists(p)) throw error(CODE_STATE, "missing chunk"); Files.copy(p, out); }
            } catch (UploadException e) { deleteQuietly(assembled); throw e; } catch (IOException e) { deleteQuietly(assembled); throw error(CODE_STAGING, e.getMessage()); } finally { deleteQuietly(reservation); }
        String sha = digestFile(assembled); if (!upload.getExpectedSha256().equals(sha)) { deleteQuietly(assembled); throw error(CODE_CHECKSUM, "archive checksum mismatch"); }
        if (!isDockerSave(assembled)) { deleteQuietly(assembled); throw error(CODE_ARCHIVE, "Docker save archive is invalid"); }
        upload.setFinalSha256(sha); upload.setState(PENDING_REVIEW); upload.setCompletedAt(now()); upload.setUpdatedAt(now()); uploadMapper.updateById(upload);
        ImageArtifactRecord artifact = artifactMapper.selectById(upload.getArtifactId()); if (artifact != null) { artifact.setSha256(sha); artifact.setReviewState(PENDING_REVIEW); artifact.setUpdatedAt(now()); artifactMapper.updateById(artifact); }
        for (int i = 0; i < upload.getTotalChunks(); i++) deleteQuietly(stagingRoot.resolve(uploadId).resolve(i + ".part"));
        return upload;
    }

    private String digestFile(Path file) { try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) { MessageDigest md = MessageDigest.getInstance("SHA-256"); byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) md.update(b, 0, n); return hex(md.digest()); } catch (Exception e) { throw error(CODE_CHECKSUM, e.getMessage()); } }
    private boolean isDockerSave(Path file) { try (InputStream raw = new BufferedInputStream(Files.newInputStream(file))) { raw.mark(2); int a = raw.read(), b = raw.read(); raw.reset(); InputStream source = (a == 0x1f && b == 0x8b) ? new GZIPInputStream(raw) : raw; BoundedInputStream in = new BoundedInputStream(source, maxFileBytes); byte[] header = new byte[TAR_BLOCK]; Set<String> entries = new HashSet<>(), refs = null; int zeroBlocks = 0; while (readFully(in, header)) { if (allZero(header)) { if (++zeroBlocks == 2) return refs != null && entries.containsAll(refs); continue; } zeroBlocks = 0; if (!validTarChecksum(header)) return false; String name = tarName(header); if (!safeTarName(name) || !entries.add(name)) return false; long size = parseOctal(header, 124, 12); if (size < 0 || size > maxFileBytes) return false; if ("manifest.json".equals(name)) { if (size > MAX_MANIFEST_BYTES) return false; byte[] json = new byte[(int) size]; if (!readFully(in, json)) return false; refs = dockerManifestRefs(json); if (refs == null || !discardExact(in, padding(size))) return false; } else if (!discardExact(in, size + padding(size))) return false; } return false; } catch (Exception e) { return false; } }
    private boolean readFully(InputStream in, byte[] b) throws IOException { int off = 0, n; while (off < b.length && (n = in.read(b, off, b.length - off)) != -1) off += n; return off == b.length; }

    private Set<String> dockerManifestRefs(byte[] json) { try { com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json); if (!root.isArray() || root.size() == 0) return null; Set<String> refs = new HashSet<>(); for (com.fasterxml.jackson.databind.JsonNode entry : root) { if (!entry.hasNonNull("Config") || !entry.has("Layers") || !entry.get("Layers").isArray()) return null; String config = entry.get("Config").asText(); if (!safeTarName(config)) return null; refs.add(config); for (com.fasterxml.jackson.databind.JsonNode layer : entry.get("Layers")) { String name = layer.asText(); if (!safeTarName(name)) return null; refs.add(name); } } return refs; } catch (IOException e) { return null; } }
    private boolean safeTarName(String name) { return name != null && !name.isEmpty() && !name.startsWith("/") && !name.contains("\\") && !name.contains("../") && !name.equals(".."); }
    private boolean allZero(byte[] b) { for (byte x : b) if (x != 0) return false; return true; }
    private String tarName(byte[] b) throws Exception { int n = 0; while (n < 100 && b[n] != 0) n++; return new String(b, 0, n, "UTF-8"); }
    private long parseOctal(byte[] b, int off, int length) { long value = 0; int end = off + length; while (off < end && (b[off] == 0 || b[off] == ' ')) off++; for (; off < end && b[off] >= '0' && b[off] <= '7'; off++) { if (value > (Long.MAX_VALUE >> 3)) return -1; value = (value << 3) + b[off] - '0'; } return value; }
    private boolean validTarChecksum(byte[] b) { long expected = parseOctal(b, 148, 8), actual = 0; if (expected < 0) return false; for (int i = 0; i < b.length; i++) actual += i >= 148 && i < 156 ? 32 : b[i] & 0xff; return expected == actual; }
    private long padding(long size) { return (TAR_BLOCK - size % TAR_BLOCK) % TAR_BLOCK; }
    private boolean discardExact(InputStream in, long bytes) throws IOException { byte[] b = new byte[8192]; while (bytes > 0) { int n = in.read(b, 0, (int) Math.min(bytes, b.length)); if (n < 0) return false; bytes -= n; } return true; }

    @Transactional public ImageUploadRecord cancel(String role, String uploadId) { requireSuperAdmin(role); ImageUploadRecord x = locked(uploadId); if (UPLOADING.equals(x.getState())) { x.setState(CANCELLED); x.setUpdatedAt(now()); uploadMapper.updateById(x); cleanupStaging(uploadId); } return x; }
    @Transactional public ImageArtifactRecord review(String role, String artifactId, ImageReviewRequest request, int reviewerId) { requireSuperAdmin(role); String d = request == null ? null : request.getDecision(); if (!APPROVED.equals(d) && !REJECTED.equals(d)) throw error(CODE_STATE, "invalid review decision"); ImageArtifactRecord a = artifactMapper.selectPendingReviewForUpdate(artifactId); if (a == null) { a = artifactMapper.selectById(artifactId); if (a == null) throw error(CODE_STATE, "artifact not found"); if (d.equals(a.getReviewState())) return a; throw error(CODE_STATE, "artifact review decision conflicts"); } a.setReviewState(d); a.setReviewedBy(reviewerId); a.setReviewedAt(now()); a.setFailureMessage(request.getReason()); a.setUpdatedAt(now()); artifactMapper.updateById(a); if (REJECTED.equals(d)) cleanupArtifactStaging(artifactId); return a; }
    public ImageUploadRecord status(String role, String uploadId) { if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) throw error(CODE_AUTHORIZATION, "admin required"); return uploadMapper.selectById(uploadId); }
    @Scheduled(fixedDelayString = "${xkp.registry.expiry-cleanup-ms:3600000}")
    @Transactional(noRollbackFor = UploadException.class)
    public void cleanupExpiredUploads() { List<ImageUploadRecord> expired = uploadMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ImageUploadRecord>().eq("state", UPLOADING).lt("expires_at", now())); for (ImageUploadRecord candidate : expired) { ImageUploadRecord upload = uploadMapper.selectForUpdate(candidate.getUploadId()); if (upload == null || !UPLOADING.equals(upload.getState()) || upload.getExpiresAt() == null || !upload.getExpiresAt().isBefore(now())) continue; upload.setState(EXPIRED); upload.setUpdatedAt(now()); if (uploadMapper.updateById(upload) == 1) cleanupStaging(upload.getUploadId()); } retryTerminalCleanup(); }
    private void retryTerminalCleanup() { if (!Files.isDirectory(stagingRoot)) return; try (Stream<Path> dirs = Files.list(stagingRoot)) { dirs.filter(Files::isDirectory).forEach(dir -> { ImageUploadRecord upload = uploadMapper.selectById(dir.getFileName().toString()); if (upload == null) return; boolean terminal = CANCELLED.equals(upload.getState()) || EXPIRED.equals(upload.getState()); if (!terminal) { ImageArtifactRecord artifact = artifactMapper.selectById(upload.getArtifactId()); terminal = artifact != null && REJECTED.equals(artifact.getReviewState()); } if (terminal) cleanupStaging(upload.getUploadId()); }); } catch (IOException ignored) { } }
    private void cleanupArtifactStaging(String artifactId) { ImageUploadRecord upload = uploadMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ImageUploadRecord>().eq("artifact_id", artifactId)); if (upload != null) cleanupStaging(upload.getUploadId()); }
    public boolean cleanupStaging(String uploadId) { Path dir = stagingRoot.resolve(uploadId).normalize(); if (!dir.getParent().equals(stagingRoot.normalize())) return false; try { if (!Files.exists(dir)) return true; try (Stream<Path> paths = Files.walk(dir)) { paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly); } return !Files.exists(dir); } catch (IOException e) { return false; } }
    private void deleteQuietly(Path path) { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }
    private void requireText(String value, String field) { if (value == null || value.trim().isEmpty()) throw error(CODE_STATE, field + " is required"); }
    private boolean validSha256(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static class BoundedInputStream extends java.io.FilterInputStream { private long remaining; BoundedInputStream(InputStream in, long limit) { super(in); remaining = limit; } public int read() throws IOException { if (remaining == 0) throw new IOException("archive exceeds limit"); int r = super.read(); if (r >= 0) remaining--; return r; } public int read(byte[] b, int o, int l) throws IOException { if (remaining == 0) throw new IOException("archive exceeds limit"); int r = super.read(b, o, (int) Math.min(l, remaining)); if (r > 0) remaining -= r; return r; } }

    private ImageUploadRecord locked(String id) { ImageUploadRecord x = uploadMapper.selectForUpdate(id); if (x == null) throw error(CODE_STATE, "upload not found"); return x; }
    private void expireIfNeeded(ImageUploadRecord x) { if (x.getExpiresAt() != null && x.getExpiresAt().isBefore(now()) && UPLOADING.equals(x.getState())) { x.setState(EXPIRED); x.setUpdatedAt(now()); uploadMapper.updateById(x); cleanupStaging(x.getUploadId()); throw error(CODE_STATE, "upload expired"); } }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private String normalize(String s) { return s == null ? null : s.toLowerCase(Locale.ROOT); }
    private IllegalArgumentException error(String code, String msg) { return new UploadException(code, msg); }
    private String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
    public static class UploadException extends IllegalArgumentException { private final String code; public UploadException(String code, String message) { super(message); this.code = code; } public String getCode() { return code; } }
}
