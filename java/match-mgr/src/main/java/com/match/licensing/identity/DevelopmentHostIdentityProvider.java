package com.match.licensing.identity;

import com.match.licensing.crypto.Digests;

public class DevelopmentHostIdentityProvider implements HostIdentityProvider {
    private final String developmentIdentity;

    public DevelopmentHostIdentityProvider(String developmentIdentity) {
        this.developmentIdentity = developmentIdentity;
    }

    @Override
    public HostIdentity load() {
        String normalized = developmentIdentity == null ? "" : developmentIdentity.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException("开发环境宿主机标识不能为空");
        }
        return new HostIdentity("sha256:" + Digests.sha256("developmentIdentity=" + normalized + "\n"),
                "DEVELOPMENT");
    }
}
