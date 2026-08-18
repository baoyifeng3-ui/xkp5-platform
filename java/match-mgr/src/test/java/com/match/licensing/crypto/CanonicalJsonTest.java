package com.match.licensing.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CanonicalJsonTest {
    private StrictJson strictJson;
    private CanonicalJson canonicalJson;

    @Before
    public void setUp() {
        strictJson = new StrictJson();
        canonicalJson = new CanonicalJson();
    }

    @Test
    public void sortsEveryObjectKeyWithoutWhitespace() {
        JsonNode value = strictJson.readTree("{\"z\":1,\"a\":{\"y\":2,\"b\":3}}");

        assertEquals("{\"a\":{\"b\":3,\"y\":2},\"z\":1}", canonicalJson.write(value));
    }

    @Test
    public void preservesArrayOrderAndJsonScalarTypes() {
        JsonNode value = strictJson.readTree("{\"values\":[3,true,null,\"text\",-2]}");

        assertEquals("{\"values\":[3,true,null,\"text\",-2]}", canonicalJson.write(value));
    }

    @Test(expected = InvalidLicenseException.class)
    public void rejectsDuplicateObjectKeys() {
        strictJson.readTree("{\"keyId\":\"first\",\"keyId\":\"second\"}");
    }

    @Test(expected = InvalidLicenseException.class)
    public void rejectsTrailingJsonContent() {
        strictJson.readTree("{\"keyId\":\"first\"} {\"extra\":true}");
    }

    @Test(expected = InvalidLicenseException.class)
    public void rejectsFloatingPointNumbers() {
        canonicalJson.write(strictJson.readTree("{\"ratio\":0.5}"));
    }
}
