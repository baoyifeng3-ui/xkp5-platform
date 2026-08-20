package com.match.entity;

import java.util.Arrays;

public enum SubjectType {
    SINGLE_CHOICE("single_choice", "单选题"),
    MULTIPLE_CHOICE("multiple_choice", "多选题"),
    TRUE_FALSE("true_false", "判断题"),
    FILL_BLANK("fill_blank", "填空题"),
    PRACTICAL("practical", "实操题");

    private final String code;
    private final String label;

    SubjectType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static SubjectType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的题型"));
    }
}
