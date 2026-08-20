package com.match.service.impl;

import com.match.entity.AnswerSheet;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AnswerImageReferenceServiceTest {
    private final AnswerImageReferenceService service = new AnswerImageReferenceService();

    @Test
    public void retainsOnlyExistingImagesAndRemovesDuplicates() {
        AnswerSheet existing = new AnswerSheet();
        existing.setAnswerImg("/files/a.jpg,/files/b.jpg");

        assertEquals(Collections.singletonList("/files/b.jpg"),
                service.validateRetainedImages(existing,
                        new String[]{"/files/b.jpg", " /files/b.jpg "}));
    }

    @Test
    public void rejectsImageThatDoesNotBelongToExistingAnswer() {
        AnswerSheet existing = new AnswerSheet();
        existing.setAnswerImg("/files/a.jpg");

        try {
            service.validateRetainedImages(existing, new String[]{"/files/other-user.jpg"});
            fail("应拒绝不属于当前作答的历史图片");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("历史图片"));
        }
    }

    @Test
    public void combinesRetainedAndNewImagesInDisplayOrder() {
        assertEquals("/files/a.jpg,/files/new.jpg",
                service.combine(Arrays.asList("/files/a.jpg", "/files/a.jpg"),
                        Collections.singletonList("/files/new.jpg")));
    }

    @Test
    public void combinesMoreThanThreeImages() {
        assertEquals("/files/a.jpg,/files/b.jpg,/files/c.jpg,/files/d.jpg",
                service.combine(Arrays.asList("/files/a.jpg", "/files/b.jpg"),
                        Arrays.asList("/files/c.jpg", "/files/d.jpg")));
    }
}
