package com.match.environment.service;

import java.util.List;
import java.util.Map;

public class ClassOfflineException extends RuntimeException {
    private final List<Map<String, Object>> offlineServers;

    public ClassOfflineException(List<Map<String, Object>> offlineServers) {
        super("部分处理服务器不在线，请确认是否忽略并继续上课");
        this.offlineServers = offlineServers;
    }

    public List<Map<String, Object>> getOfflineServers() {
        return offlineServers;
    }
}
