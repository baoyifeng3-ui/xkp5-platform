package com.match.resource.service;

import com.match.util.dfs.FastDFSClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;

@Service
public class ResourceStorageService {
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择需要上传的文件");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.getInputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            }
            String storageKey = FastDFSClient.uploadFile(file);
            if (storageKey == null || storageKey.trim().isEmpty()) {
                throw new IllegalStateException("文件存储服务不可用");
            }
            return new StoredFile(storageKey, file.getSize(), contentType(file), hex(digest.digest()));
        } catch (ResourceOperationException error) {
            throw error;
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new ResourceOperationException(503, "STORAGE_UNAVAILABLE",
                    "文件存储服务不可用，请稍后重试");
        }
    }

    public boolean delete(String storageKey) { return FastDFSClient.deleteFile(storageKey); }
    public String accessUrl(String storageKey) { return FastDFSClient.getServerAccessUrl(storageKey); }

    private String contentType(MultipartFile file) {
        return file.getContentType() == null || file.getContentType().trim().isEmpty()
                ? "application/octet-stream" : file.getContentType();
    }
    private String hex(byte[] bytes) { StringBuilder value = new StringBuilder(64); for (byte item : bytes) value.append(String.format("%02x", item & 0xff)); return value.toString(); }

    public static class StoredFile {
        private final String storageKey; private final long contentLength;
        private final String mimeType; private final String sha256;
        StoredFile(String storageKey, long contentLength, String mimeType, String sha256) {
            this.storageKey = storageKey; this.contentLength = contentLength;
            this.mimeType = mimeType; this.sha256 = sha256;
        }
        public String getStorageKey() { return storageKey; }
        public long getContentLength() { return contentLength; }
        public String getMimeType() { return mimeType; }
        public String getSha256() { return sha256; }
    }
}
