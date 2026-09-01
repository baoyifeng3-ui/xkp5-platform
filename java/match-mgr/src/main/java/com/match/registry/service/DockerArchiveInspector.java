package com.match.registry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

final class DockerArchiveInspector {
    private static final int TAR_BLOCK = 512;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private final ObjectMapper mapper = new ObjectMapper();

    int countLayers(Path archive) {
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(archive))) {
            raw.mark(2);
            int first = raw.read();
            int second = raw.read();
            raw.reset();
            InputStream input = first == 0x1f && second == 0x8b ? new GZIPInputStream(raw) : raw;
            byte[] header = new byte[TAR_BLOCK];
            while (readFully(input, header)) {
                if (allZero(header)) break;
                long size = parseOctal(header, 124, 12);
                if (size < 0) throw invalid("Invalid TAR entry size");
                String name = tarName(header);
                if ("manifest.json".equals(name)) {
                    if (size > MAX_MANIFEST_BYTES) throw invalid("Docker manifest is too large");
                    byte[] manifest = new byte[(int) size];
                    if (!readFully(input, manifest)) throw invalid("Docker manifest is truncated");
                    return uniqueLayerCount(manifest);
                }
                skipExact(input, size + padding(size));
            }
            throw invalid("Docker manifest is missing");
        } catch (RegistryImportTool.ImportException e) {
            throw e;
        } catch (IOException e) {
            throw new RegistryImportTool.ImportException("ARCHIVE_INSPECTION_FAILED",
                    "Docker archive cannot be inspected", e);
        }
    }

    private int uniqueLayerCount(byte[] manifest) throws IOException {
        JsonNode root = mapper.readTree(manifest);
        if (root == null || !root.isArray() || root.size() == 0) throw invalid("Docker manifest is invalid");
        Set<String> layers = new HashSet<>();
        for (JsonNode image : root) {
            JsonNode list = image.get("Layers");
            if (list == null || !list.isArray()) throw invalid("Docker manifest has no layers");
            for (JsonNode layer : list) {
                if (layer.isTextual() && !layer.asText().trim().isEmpty()) layers.add(layer.asText());
            }
        }
        return layers.size();
    }

    private boolean readFully(InputStream input, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int count = input.read(target, offset, target.length - offset);
            if (count < 0) return false;
            offset += count;
        }
        return true;
    }

    private void skipExact(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else if (input.read() < 0) {
                throw invalid("Docker archive is truncated");
            } else {
                remaining--;
            }
        }
    }

    private String tarName(byte[] header) {
        int length = 0;
        while (length < 100 && header[length] != 0) length++;
        return new String(header, 0, length, java.nio.charset.StandardCharsets.UTF_8);
    }

    private long parseOctal(byte[] value, int offset, int length) {
        long result = 0;
        int end = offset + length;
        while (offset < end && (value[offset] == 0 || value[offset] == ' ')) offset++;
        for (; offset < end && value[offset] >= '0' && value[offset] <= '7'; offset++) {
            if (result > (Long.MAX_VALUE >> 3)) return -1;
            result = (result << 3) + value[offset] - '0';
        }
        return result;
    }

    private long padding(long size) {
        return (TAR_BLOCK - size % TAR_BLOCK) % TAR_BLOCK;
    }

    private boolean allZero(byte[] value) {
        for (byte current : value) if (current != 0) return false;
        return true;
    }

    private RegistryImportTool.ImportException invalid(String message) {
        return new RegistryImportTool.ImportException("INVALID_DOCKER_ARCHIVE", message);
    }
}
