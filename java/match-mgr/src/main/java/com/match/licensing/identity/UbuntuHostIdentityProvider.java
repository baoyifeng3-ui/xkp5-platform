package com.match.licensing.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.match.licensing.crypto.Digests;
import com.match.licensing.crypto.StrictJson;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class UbuntuHostIdentityProvider implements HostIdentityProvider {
    private final Path identityFile;
    private final String operatingSystem;
    private final StrictJson strictJson;

    public UbuntuHostIdentityProvider(Path identityFile, String operatingSystem, StrictJson strictJson) {
        this.identityFile = identityFile;
        this.operatingSystem = operatingSystem;
        this.strictJson = strictJson;
    }

    @Override
    public HostIdentity load() {
        if (operatingSystem == null || !operatingSystem.toLowerCase(Locale.ROOT).contains("linux")) {
            throw new IllegalStateException("正式授权只能绑定 Ubuntu 管理服务器");
        }
        JsonNode root = readIdentityFile();
        if (!root.isObject()) {
            throw new IllegalStateException("宿主机身份文件格式无效");
        }

        List<String> normalized = new ArrayList<>();
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode valueNode = root.get(name);
            if (valueNode == null || !valueNode.isTextual()) {
                throw new IllegalStateException("宿主机身份文件格式无效");
            }
            String value = valueNode.asText().trim();
            if (!value.isEmpty()) {
                normalized.add(name.trim() + "=" + value.toLowerCase(Locale.ROOT));
            }
        }
        if (normalized.size() < 2) {
            throw new IllegalStateException("生产环境至少需要两个宿主机标识");
        }
        Collections.sort(normalized);
        StringBuilder canonical = new StringBuilder();
        for (String line : normalized) {
            canonical.append(line).append('\n');
        }
        return new HostIdentity("sha256:" + Digests.sha256(canonical.toString()), "PRODUCTION");
    }

    private JsonNode readIdentityFile() {
        try {
            byte[] content = Files.readAllBytes(identityFile);
            return strictJson.readTree(new String(content, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException) {
                throw (IllegalStateException) exception;
            }
            throw new IllegalStateException("无法读取宿主机身份文件", exception);
        }
    }
}
