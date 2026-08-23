package com.match.course.web;

import com.match.security.RoleGuard;
import com.match.util.dfs.FastDFSClient;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/course-resources")
public class CourseResourceUploadController {
    private final RoleGuard roleGuard;

    public CourseResourceUploadController(RoleGuard roleGuard) { this.roleGuard = roleGuard; }

    @PostMapping("/upload")
    public ResponseResult<Object> upload(@RequestPart("file") MultipartFile file) throws Exception {
        roleGuard.requireAnyAdmin();
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("资源文件不能为空");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        String storageKey = FastDFSClient.uploadFile(file);
        if (storageKey == null || storageKey.trim().isEmpty()) throw new IllegalArgumentException("资源存储失败");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", file.getOriginalFilename()); result.put("storageKey", storageKey);
        result.put("contentLength", file.getSize()); result.put("mimeType", file.getContentType());
        result.put("sha256", hex(digest.digest()));
        return Response.makeOKRsp(result);
    }

    private String hex(byte[] bytes) { StringBuilder value = new StringBuilder(); for (byte b : bytes) value.append(String.format("%02x", b)); return value.toString(); }
}
