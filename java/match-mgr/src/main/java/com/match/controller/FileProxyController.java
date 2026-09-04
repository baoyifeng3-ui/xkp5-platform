package com.match.controller;

import com.match.util.dfs.FastDFSClient;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/files")
public class FileProxyController {
    @GetMapping("/**")
    public ResponseEntity<byte[]> file(HttpServletRequest request) {
        String key = request.getRequestURI().substring("/files/".length());
        if (!key.matches("[A-Za-z0-9._/-]+") || key.contains("..")) throw new IllegalArgumentException("文件地址无效");
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        String lower = key.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) type = MediaType.IMAGE_JPEG;
        else if (lower.endsWith(".png")) type = MediaType.IMAGE_PNG;
        else if (lower.endsWith(".webp")) type = MediaType.parseMediaType("image/webp");
        else if (lower.endsWith(".pdf")) type = MediaType.APPLICATION_PDF;
        else if (lower.endsWith(".txt") || lower.endsWith(".csv") || lower.endsWith(".log") || lower.endsWith(".md")) type = MediaType.TEXT_PLAIN;
        else if (lower.endsWith(".json")) type = MediaType.APPLICATION_JSON;
        else if (lower.endsWith(".html") || lower.endsWith(".htm")) type = MediaType.TEXT_HTML;
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS)).contentType(type)
                .body(FastDFSClient.downloadBytes(key));
    }
}
