package com.match.service.impl;

import java.util.List;

public class SubmissionValidationException extends IllegalArgumentException {
    private final List<Integer> subjectIds;

    public SubmissionValidationException(List<Integer> subjectIds, String message) {
        super(message);
        this.subjectIds = subjectIds;
    }

    public List<Integer> getSubjectIds() {
        return subjectIds;
    }
}
