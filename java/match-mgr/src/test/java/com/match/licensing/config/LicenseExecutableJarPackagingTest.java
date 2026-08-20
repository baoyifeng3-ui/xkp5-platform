package com.match.licensing.config;

import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;

import static org.junit.Assert.assertEquals;

public class LicenseExecutableJarPackagingTest {
    @Test
    public void executableJarUnpacksBouncyCastleProvider() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document pom = factory.newDocumentBuilder().parse(new File("pom.xml"));

        String expression = "count(//*[local-name()='plugin']"
                + "[*[local-name()='artifactId']='spring-boot-maven-plugin']"
                + "/*[local-name()='configuration']/*[local-name()='requiresUnpack']"
                + "/*[local-name()='dependency']"
                + "[*[local-name()='groupId']='org.bouncycastle']"
                + "[*[local-name()='artifactId']='bcprov-jdk15on'])";
        Double count = (Double) XPathFactory.newInstance().newXPath()
                .evaluate(expression, pom, XPathConstants.NUMBER);

        assertEquals("Bouncy Castle must be unpacked from the executable Spring Boot jar",
                1.0d, count, 0.0d);
    }
}
