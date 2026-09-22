package com.match.controller;

import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 训练模型验证的远程图片转 base64 辅助端点。
 * <p>
 * 兼容前端 TrainingValidation.vue 的“图片 URL”验证模式（沿用旧
 * /sample/httpToBase64 路径约定）。旧 SampleController 随基线重构被移除，
 * 但前端新页面仍在调用，缺失时该模式会 404。
 */
@RestController
@RequestMapping("/sample")
@Api
public class SampleController {

    @PostMapping("/httpToBase64")
    public ResponseResult<Object> httpToBase64(@RequestBody(required = false) Map<String, String> body) {
        String url = body == null ? null : body.get("url");
        if (url == null || url.trim().isEmpty()) {
            return Response.makeRsp(400, "url 不能为空");
        }
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            return Response.makeRsp(400, "仅支持 http/https 图片地址");
        }
        try {
            String base64 = downloadToBase64(normalized);
            if (base64 == null) {
                return Response.makeErrRsp("远程图片获取失败");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("base64", base64);
            return Response.makeOKRsp(data);
        } catch (Exception e) {
            return Response.makeErrRsp("远程图片获取失败");
        }
    }

    private String downloadToBase64(String urlText) throws Exception {
        URL url = new URL(urlText);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("GET");
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return Base64.getEncoder().encodeToString(buffer.toByteArray());
        } finally {
            connection.disconnect();
        }
    }
}
