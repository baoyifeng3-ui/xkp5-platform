package com.match.controller;

import org.junit.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class FileProxyControllerTest {
    @Test
    public void exposesPublicFilesPathAndReturnsStoredBytes() throws Exception {
        assertEquals("/files", FileProxyController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertArrayEquals(new String[]{"/**"}, FileProxyController.class.getMethod("file", javax.servlet.http.HttpServletRequest.class)
                .getAnnotation(GetMapping.class).value());
    }
}
