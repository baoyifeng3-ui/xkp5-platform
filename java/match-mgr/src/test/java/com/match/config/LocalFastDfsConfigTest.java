package com.match.config;

import com.github.tobato.fastdfs.domain.fdfs.StorageNode;
import com.github.tobato.fastdfs.domain.fdfs.StorageNodeInfo;
import com.github.tobato.fastdfs.service.TrackerClient;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertTrue;

public class LocalFastDfsConfigTest {
    private TrackerClient delegate;
    private TrackerClient client;

    @Before
    public void setUp() {
        delegate = mock(TrackerClient.class);
        client = new LocalFastDfsConfig.LocalTrackerClient(delegate, "127.0.0.1");
    }

    @Test
    public void rewritesUploadStorageAddressToLocalPortMapping() {
        StorageNode node = new StorageNode();
        node.setIp("172.18.0.4");
        node.setPort(23000);
        when(delegate.getStoreStorage()).thenReturn(node);

        StorageNode result = client.getStoreStorage();

        assertEquals("127.0.0.1", result.getIp());
        assertEquals(23000, result.getPort());
    }

    @Test
    public void rewritesDownloadStorageAddressToLocalPortMapping() {
        StorageNodeInfo node = new StorageNodeInfo();
        node.setIp("172.18.0.4");
        node.setPort(23000);
        when(delegate.getFetchStorage("group1", "M00/image.jpg")).thenReturn(node);

        StorageNodeInfo result = client.getFetchStorage("group1", "M00/image.jpg");

        assertEquals("127.0.0.1", result.getIp());
        assertEquals(23000, result.getPort());
    }

    @Test
    public void localDockerProfileDefaultsToStorageServiceName() throws Exception {
        InputStream input = getClass().getResourceAsStream("/application-local.yml");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048]; int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        String yaml = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("MATCH_FASTDFS_STORAGE_HOST:fastdfs-storage"));
    }
}
