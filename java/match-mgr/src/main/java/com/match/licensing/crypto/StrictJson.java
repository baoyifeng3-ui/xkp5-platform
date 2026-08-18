package com.match.licensing.crypto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class StrictJson {
    private final ObjectMapper mapper;

    public StrictJson() {
        mapper = new ObjectMapper();
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        mapper.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    public JsonNode readTree(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new InvalidLicenseException("INVALID_JSON", "授权文件内容为空");
        }
        try (JsonParser parser = mapper.getFactory().createParser(content)) {
            JsonNode value = mapper.readTree(parser);
            if (value == null || parser.nextToken() != null) {
                throw new InvalidLicenseException("INVALID_JSON", "授权文件包含多余内容");
            }
            return value;
        } catch (InvalidLicenseException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new InvalidLicenseException("INVALID_JSON", "授权文件不是有效的 JSON", exception);
        }
    }
}
