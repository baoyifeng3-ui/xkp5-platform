package com.match.registry.service;

import com.match.registry.persistence.ImageFileMapper;
import com.match.registry.persistence.ImageFileRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ImageFileService {
    private final ImageFileMapper mapper;
    private final Path root;
    private final Clock clock;

    @Autowired
    public ImageFileService(ImageFileMapper mapper,
                            @Value("${xkp.registry.staging-dir:${XKP_REGISTRY_STAGING_DIR:/data/registry-staging}}") String root) {
        this(mapper, java.nio.file.Paths.get(root), Clock.systemUTC());
    }

    ImageFileService(ImageFileMapper mapper, Path root, Clock clock) {
        this.mapper = mapper; this.root = root.toAbsolutePath().normalize(); this.clock = clock;
    }

    public boolean exists(String repository, String filename) {
        return findByFilename(filename) != null || Files.isRegularFile(path(repository, filename));
    }

    public ImageFileRecord findByFilename(String filename) { return mapper.selectByFilename(filename); }

    static void validateFilename(String filename) {
        if (filename == null || filename.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 240
                || !filename.matches("[\\p{L}\\p{N}._-]+\\.tar") || filename.contains(".."))
            throw new IllegalArgumentException("仅支持安全命名的 .tar 文件");
    }

    static String repositoryFromFilename(String filename) {
        validateFilename(filename);
        String stem = filename.substring(0, filename.length() - 4).toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (stem.isEmpty()) stem = "image";
        if (stem.length() > 48) stem = stem.substring(0, 48).replaceAll("-$", "");
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(filename.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder suffix = new StringBuilder();
            for (byte value : digest) suffix.append(String.format("%02x", value & 0xff));
            return "xkp/files/" + stem + "-" + suffix;
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public Path archive(ImageFileRecord file) {
        Path path = root.resolve(file.getArchivePath()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IllegalArgumentException("镜像归档文件不存在");
        return path;
    }

    public List<ImageFileRecord> list(int limit) { return mapper.selectEnabled(Math.max(1, Math.min(limit, 500))); }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void rebuildIndex() {
        Path files = root.resolve("files"); if (!Files.isDirectory(files)) return;
        try (Stream<Path> paths = Files.walk(files)) {
            paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".tar")).forEach(path -> {
                Path relative = files.relativize(path); if (relative.getNameCount() < 2) return;
                String repository = relative.getParent().toString().replace('\\', '/'); String filename = relative.getFileName().toString();
                ImageFileRecord file = mapper.selectByIdentity(repository, filename); if (file != null) return;
                LocalDateTime now = LocalDateTime.now(clock); file = new ImageFileRecord(); file.setFileId(UUID.randomUUID().toString());
                file.setImageRepository(repository); file.setOriginalFilename(filename);
                file.setImageTag(repository.matches("xkp/files/[a-z0-9-]+-[a-f0-9]{64}") ? "latest" : tagFrom(filename));
                file.setArchivePath(root.relativize(path).toString().replace('\\', '/')); try { file.setSizeBytes(Files.size(path)); } catch (IOException e) { return; }
                file.setEnabled(true); file.setCreatedBy(0); file.setCreatedAt(now); file.setUpdatedAt(now); mapper.insert(file);
            });
        } catch (IOException e) { throw new IllegalStateException("扫描镜像归档目录失败", e); }
    }

    public ImageFileRecord get(String fileId) {
        ImageFileRecord file = mapper.selectById(fileId);
        if (file == null || !Boolean.TRUE.equals(file.getEnabled())) throw new IllegalArgumentException("镜像文件不存在或未启用");
        return file;
    }

    @Transactional
    public ImageFileRecord enable(String repository, String filename, String tag, Path source, int actorId) {
        ImageFileRecord file = mapper.selectByFilename(filename);
        if (file != null) { repository = file.getImageRepository(); tag = file.getImageTag(); }
        Path target = path(repository, filename);
        try {
            Files.createDirectories(target.getParent());
            try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new IllegalArgumentException("保存镜像归档失败: " + e.getMessage(), e); }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean created = file == null;
        if (file == null) {
            file = new ImageFileRecord(); file.setFileId(UUID.randomUUID().toString()); file.setCreatedBy(actorId); file.setCreatedAt(now);
            file.setImageRepository(repository); file.setOriginalFilename(filename);
        }
        file.setImageTag(tag == null || tag.trim().isEmpty() ? "latest" : tag.trim());
        file.setArchivePath(root.relativize(target).toString().replace('\\', '/'));
        try { file.setSizeBytes(Files.size(target)); } catch (IOException e) { throw new IllegalArgumentException("读取镜像归档大小失败", e); }
        file.setEnabled(true); file.setUpdatedAt(now);
        if (created) mapper.insert(file); else mapper.updateById(file);
        return file;
    }

    @Transactional
    public void delete(String fileId) {
        ImageFileRecord file = mapper.selectById(fileId); if (file == null) return;
        if (mapper.countTemplateReferences(file.getImageRepository() + ":" + file.getImageTag()) > 0
                || mapper.countFileTemplateReferences(fileId) > 0)
            throw new IllegalArgumentException("镜像已被容器模板引用，请先删除关联模板");
        Path path = root.resolve(file.getArchivePath()).normalize();
        try { if (path.startsWith(root)) Files.deleteIfExists(path); } catch (IOException e) { throw new IllegalArgumentException("删除镜像归档失败", e); }
        mapper.deleteById(fileId);
    }

    private Path path(String repository, String filename) {
        if (repository == null || !repository.matches("[a-z0-9]+(?:[._/-][a-z0-9]+)*") || repository.contains(".."))
            throw new IllegalArgumentException("镜像仓库名称无效");
        validateFilename(filename);
        Path path = root.resolve("files").resolve(repository).resolve(filename).normalize();
        if (!path.startsWith(root.resolve("files"))) throw new IllegalArgumentException("镜像文件路径无效");
        return path;
    }

    private String tagFrom(String filename) { return filename.substring(0, filename.length() - 4); }
}
