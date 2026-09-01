package com.match.resource.service;

public enum ResourceSpaceType {
    COURSE, PUBLIC, EXCHANGE, HOMEWORK;

    public static ResourceSpaceType parse(String value) {
        if (value == null) throw new IllegalArgumentException("资源空间不能为空");
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("资源空间无效");
        }
    }
}
