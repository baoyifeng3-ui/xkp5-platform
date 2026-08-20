package com.match.service.impl;

import com.match.entity.AnswerSheet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AnswerImageReferenceService {
    public List<String> validateRetainedImages(AnswerSheet existing, String[] requestedImages) {
        Set<String> existingImages = split(existing == null ? null : existing.getAnswerImg());
        Set<String> requested = normalize(requestedImages == null
                ? new ArrayList<String>()
                : Arrays.asList(requestedImages));
        if (!existingImages.containsAll(requested)) {
            throw new IllegalArgumentException("包含无效的历史图片");
        }
        return new ArrayList<>(requested);
    }

    public String combine(Collection<String> retainedImages, Collection<String> uploadedImages) {
        LinkedHashSet<String> images = new LinkedHashSet<>();
        images.addAll(normalize(retainedImages));
        images.addAll(normalize(uploadedImages));
        return String.join(",", images);
    }

    private Set<String> split(String answerImg) {
        if (answerImg == null || answerImg.trim().isEmpty()) {
            return new LinkedHashSet<>();
        }
        return normalize(Arrays.asList(answerImg.split(",")));
    }

    private Set<String> normalize(Collection<String> images) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (images == null) {
            return normalized;
        }
        for (String image : images) {
            if (image != null && !image.trim().isEmpty()) {
                normalized.add(image.trim());
            }
        }
        return normalized;
    }
}
