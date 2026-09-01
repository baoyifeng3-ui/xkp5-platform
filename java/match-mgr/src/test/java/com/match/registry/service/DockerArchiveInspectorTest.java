package com.match.registry.service;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class DockerArchiveInspectorTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void countsUniqueLayersFromDockerSaveManifest() throws Exception {
        String manifest = "[{\"Config\":\"config.json\",\"RepoTags\":[\"xkp/code:v1\"],"
                + "\"Layers\":[\"a/layer.tar\",\"b/layer.tar\",\"a/layer.tar\"]}]";
        Path archive = temp.newFile("image.tar").toPath();
        Files.write(archive, tarEntry("manifest.json", manifest.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, new DockerArchiveInspector().countLayers(archive));
    }

    private byte[] tarEntry(String name, byte[] content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] header = new byte[512];
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
        writeOctal(header, 100, 8, 0644);
        writeOctal(header, 108, 8, 0);
        writeOctal(header, 116, 8, 0);
        writeOctal(header, 124, 12, content.length);
        writeOctal(header, 136, 12, 0);
        for (int index = 148; index < 156; index++) header[index] = ' ';
        header[156] = '0';
        System.arraycopy("ustar\0".getBytes(StandardCharsets.US_ASCII), 0, header, 257, 6);
        long checksum = 0;
        for (byte value : header) checksum += value & 0xff;
        writeOctal(header, 148, 8, checksum);
        output.write(header);
        output.write(content);
        output.write(new byte[(512 - content.length % 512) % 512]);
        output.write(new byte[1024]);
        return output.toByteArray();
    }

    private void writeOctal(byte[] target, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        int start = offset + length - octal.length() - 1;
        for (int index = offset; index < start; index++) target[index] = '0';
        byte[] bytes = octal.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, start, bytes.length);
        target[offset + length - 1] = 0;
    }
}
