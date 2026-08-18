package com.match.config;

import com.github.tobato.fastdfs.domain.fdfs.StorageNode;
import com.github.tobato.fastdfs.domain.fdfs.StorageNodeInfo;
import com.github.tobato.fastdfs.service.TrackerClient;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
