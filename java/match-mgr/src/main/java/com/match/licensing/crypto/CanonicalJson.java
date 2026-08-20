package com.match.licensing.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class CanonicalJson {
    private final ObjectMapper mapper = new ObjectMapper();

    public String write(JsonNode value) {
        try {
            return mapper.writeValueAsString(normalize(value));
        } catch (InvalidLicenseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidLicenseException("INVALID_JSON", "无法规范化授权内容", exception);
        }
    }

    public byte[] writeBytes(JsonNode value) {
        return write(value).getBytes(StandardCharsets.UTF_8);
    }

    private JsonNode normalize(JsonNode value) {
        if (value == null) {
            throw new InvalidLicenseException("INVALID_JSON", "授权内容不能为空");
        }
        if (value.isFloatingPointNumber()) {
            throw new InvalidLicenseException("UNSUPPORTED_NUMBER", "授权内容不能包含小数");
        }
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            Iterator<String> fields = value.fieldNames();
            while (fields.hasNext()) {
                names.add(fields.next());
            }
            Collections.sort(names);
            for (String name : names) {
                result.set(name, normalize(value.get(name)));
            }
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            for (JsonNode item : value) {
                result.add(normalize(item));
            }
            return result;
        }
        if (value.isIntegralNumber() || value.isTextual() || value.isBoolean() || value.isNull()) {
            return value.deepCopy();
        }
        throw new InvalidLicenseException("UNSUPPORTED_JSON_TYPE", "授权内容包含不支持的数据类型");
    }
}
