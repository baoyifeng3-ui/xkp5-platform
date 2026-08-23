package com.match.agent.service;

import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentConnectivityService {
    private static final int TIMEOUT_MILLIS = 1500;

    public Map<String, Object> check(String serverIp) {
        if (serverIp == null || !serverIp.trim().matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
            throw new IllegalArgumentException("服务器 IP 格式无效");
        }
        String normalizedIp = serverIp.trim();
        boolean reachable;
        try {
            reachable = InetAddress.getByName(normalizedIp).isReachable(TIMEOUT_MILLIS);
        } catch (Exception exception) {
            reachable = false;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverIp", normalizedIp);
        result.put("reachable", reachable);
        result.put("checkedAt", System.currentTimeMillis());
        return result;
    }
}
