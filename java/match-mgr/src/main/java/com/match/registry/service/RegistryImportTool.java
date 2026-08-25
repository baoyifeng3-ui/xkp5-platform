package com.match.registry.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "xkp.registry.import", name = "enabled", havingValue = "true")
public class RegistryImportTool {
    static final int MAX_CAPTURE_BYTES = 64 * 1024;
    private static final int MAX_CREDENTIAL_BYTES = 4096;
    private static final Pattern REPOSITORY = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*(?:/[a-z0-9]+(?:[._-][a-z0-9]+)*)*");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");
    private static final Pattern REGISTRY_AUTHORITY = Pattern.compile("[A-Za-z0-9.-]+(?::[0-9]{1,5})?");

    private final String executable;
    private final String registryAuthority;
    private final Path registryCaFile;
    private final Path usernameFile;
    private final Path passwordFile;
    private final Path importRoot;
    private final long timeoutSeconds;
    private final ProcessLauncher launcher;
    private final long terminationWaitMillis;
    private final long collectorJoinMillis;

    @org.springframework.beans.factory.annotation.Autowired
    public RegistryImportTool(
            @Value("${xkp.registry.import.executable:skopeo}") String executable,
            @Value("${xkp.registry.url:${REGISTRY_URL:https://registry:5000}}") String registryUrl,
            @Value("${xkp.registry.ca-file:${REGISTRY_CA_FILE:/etc/xkp/registry-tls/ca.crt}}") String registryCaFile,
            @Value("${xkp.registry.username-file:${REGISTRY_USERNAME_FILE:/run/xkp/registry-secrets/username}}") String usernameFile,
            @Value("${xkp.registry.password-file:${REGISTRY_PASSWORD_FILE:/run/xkp/registry-secrets/password}}") String passwordFile,
            @Value("${xkp.registry.import-root:${REGISTRY_IMPORT_ROOT:/data/registry-import}}") String importRoot,
            @Value("${xkp.registry.import.timeout-seconds:3600}") long timeoutSeconds) {
        this.executable = executable;
        this.registryAuthority = authority(registryUrl);
        this.registryCaFile = Paths.get(registryCaFile);
        this.usernameFile = Paths.get(usernameFile);
        this.passwordFile = Paths.get(passwordFile);
        this.importRoot = Paths.get(importRoot);
        this.timeoutSeconds = timeoutSeconds;
        this.launcher = command -> new ProcessBuilder(command).start();
        this.terminationWaitMillis = 5000;
        this.collectorJoinMillis = 5000;
    }

    RegistryImportTool(String executable, String registryUrl, Path registryCaFile,
                       Path usernameFile, Path passwordFile, Path importRoot,
                       long timeoutSeconds, ProcessLauncher launcher,
                       long terminationWaitMillis, long collectorJoinMillis) {
        this.executable = executable;
        this.registryAuthority = authority(registryUrl);
        this.registryCaFile = registryCaFile;
        this.usernameFile = usernameFile;
        this.passwordFile = passwordFile;
        this.importRoot = importRoot;
        this.timeoutSeconds = timeoutSeconds;
        this.launcher = launcher;
        this.terminationWaitMillis = terminationWaitMillis;
        this.collectorJoinMillis = collectorJoinMillis;
    }

    public ImportResult importArchive(ImportRequest request) {
        validate(request);
        Path digestFile = null;
        Path authFile = null;
        Process process = null;
        BoundedStreamCollector stdout = null;
        BoundedStreamCollector stderr = null;
        try {
            Files.createDirectories(importRoot);
            digestFile = Files.createTempFile(importRoot, "registry-digest-", ".txt");
            String credentials = readCredential(usernameFile) + ":" + readCredential(passwordFile);
            authFile = Files.createTempFile(importRoot, "registry-auth-", ".json");
            String encodedCredentials = Base64.getEncoder().encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8));
            Files.write(authFile, ("{\"auths\":{\"" + registryAuthority
                    + "\":{\"auth\":\"" + encodedCredentials + "\"}}}")
                    .getBytes(StandardCharsets.UTF_8));
            restrictToOwner(authFile);
            List<String> command = new ArrayList<>();
            command.add(executable);
            command.add("copy");
            command.add("--digestfile");
            command.add(digestFile.toString());
            command.add("--dest-tls-verify=true");
            command.add("--dest-cert-dir");
            command.add(registryCaFile.getParent().toString());
            command.add("--dest-authfile");
            command.add(authFile.toString());
            command.add("docker-archive:" + request.getArchive().toAbsolutePath());
            command.add("docker://" + registryAuthority + "/" + request.getRepository()
                    + ":" + request.getTag());

            process = launcher.start(command);
            stdout = new BoundedStreamCollector(process.getInputStream());
            stderr = new BoundedStreamCollector(process.getErrorStream());
            stdout.start();
            stderr.start();
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                terminate(process);
            }
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
            stdout.join(collectorJoinMillis);
            stderr.join(collectorJoinMillis);
            if (!completed) {
                throw new ImportException("IMPORT_TIMEOUT", "Registry import timed out: " + stderr.text());
            }
            if (process.exitValue() != 0) {
                throw new ImportException("TOOL_FAILED", boundedMessage(stderr.text(), stdout.text()));
            }
            String digest = new String(Files.readAllBytes(digestFile), StandardCharsets.US_ASCII)
                    .trim().toLowerCase(Locale.ROOT);
            return new ImportResult(digest, stdout.text());
        } catch (ImportException e) {
            throw e;
        } catch (InterruptedException e) {
            if (process != null) {
                terminate(process);
            }
            closeQuietly(process == null ? null : process.getInputStream());
            closeQuietly(process == null ? null : process.getErrorStream());
            joinCollector(stdout);
            joinCollector(stderr);
            Thread.currentThread().interrupt();
            throw new ImportException("IMPORT_INTERRUPTED", "Registry import interrupted", e);
        } catch (IOException e) {
            throw new ImportException("TOOL_FAILED", safeMessage(e), e);
        } finally {
            if (digestFile != null) {
                try {
                    Files.deleteIfExists(digestFile);
                } catch (IOException ignored) {
                    // A stale result file contains no credentials and can be cleaned later.
                }
            }
            if (authFile != null) {
                try {
                    Files.deleteIfExists(authFile);
                } catch (IOException ignored) {
                    // Import root is private to the dedicated worker and cleaned operationally.
                }
            }
        }
    }

    private void terminate(Process process) {
        process.destroyForcibly();
        try {
            if (!process.waitFor(terminationWaitMillis, TimeUnit.MILLISECONDS)) {
                process.destroy();
            }
        } catch (InterruptedException e) {
            process.destroy();
            Thread.currentThread().interrupt();
        }
    }

    private void closeQuietly(InputStream stream) {
        if (stream == null) return;
        try { stream.close(); } catch (IOException ignored) { }
    }

    private void joinCollector(BoundedStreamCollector collector) {
        if (collector == null) return;
        try {
            collector.join(collectorJoinMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void validate(ImportRequest request) {
        if (request == null || request.getArchive() == null || !Files.isRegularFile(request.getArchive())) {
            throw new ImportException("ARCHIVE_NOT_FOUND", "Staging archive does not exist");
        }
        if (request.getRepository() == null || !REPOSITORY.matcher(request.getRepository()).matches()) {
            throw new ImportException("INVALID_IMAGE_REFERENCE", "Invalid image repository");
        }
        if (request.getTag() == null || !TAG.matcher(request.getTag()).matches()) {
            throw new ImportException("INVALID_IMAGE_REFERENCE", "Invalid image tag");
        }
    }

    private static String authority(String registryUrl) {
        String normalized = registryUrl == null ? "" : registryUrl.trim();
        normalized = normalized.replaceFirst("^https?://", "");
        normalized = normalized.replaceFirst("/+$", "");
        if (!REGISTRY_AUTHORITY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid Registry URL");
        }
        return normalized;
    }

    private static void restrictToOwner(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // The production importer is Linux; non-POSIX test hosts use their native ACLs.
        }
    }

    private static String readCredential(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_CREDENTIAL_BYTES) {
            throw new IOException("Registry credential file is missing or too large");
        }
        String value = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
        if (value.isEmpty() || value.indexOf('\0') >= 0) {
            throw new IOException("Registry credential file is empty or invalid");
        }
        return value;
    }

    private static String boundedMessage(String stderr, String stdout) {
        String value = stderr == null || stderr.trim().isEmpty() ? stdout : stderr;
        return value == null || value.trim().isEmpty() ? "Registry import tool failed" : value.trim();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static final class BoundedStreamCollector extends Thread {
        private final InputStream input;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

        private BoundedStreamCollector(InputStream input) {
            this.input = input;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] buffer = new byte[8192];
            try {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    int remaining = MAX_CAPTURE_BYTES - captured.size();
                    if (remaining > 0) {
                        captured.write(buffer, 0, Math.min(count, remaining));
                    }
                }
            } catch (IOException ignored) {
                // Process exit handling reports the useful bounded output already captured.
            }
        }

        private String text() {
            return new String(captured.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static final class ImportRequest {
        private final Path archive;
        private final String repository;
        private final String tag;

        public ImportRequest(Path archive, String repository, String tag) {
            this.archive = archive;
            this.repository = repository;
            this.tag = tag;
        }

        public Path getArchive() {
            return archive;
        }

        public String getRepository() {
            return repository;
        }

        public String getTag() {
            return tag;
        }
    }

    interface ProcessLauncher {
        Process start(List<String> command) throws IOException;
    }

    public static final class ImportResult {
        private final String digest;
        private final String output;

        public ImportResult(String digest, String output) {
            this.digest = digest;
            this.output = output;
        }

        public String getDigest() {
            return digest;
        }

        public String getOutput() {
            return output;
        }
    }

    public static class ImportException extends RuntimeException {
        private final String code;

        public ImportException(String code, String message) {
            super(message);
            this.code = code;
        }

        public ImportException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
