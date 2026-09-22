package com.match.controller;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class SampleValidationRegressionTest {
    @Test public void invalidInputDoesNotExposeStackTrace() {
        SampleController controller = new SampleController();
        String message = controller.httpToBase64(Collections.singletonMap("url", "invalid")).getMsg();
        assertFalse(message.contains("Exception"));
        assertFalse(message.contains("\tat "));
        assertFalse(controller.httpToBase64(null).getMsg().contains("Exception"));
    }
}
