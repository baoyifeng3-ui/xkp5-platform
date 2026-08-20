package com.match.config;

import com.github.tobato.fastdfs.domain.fdfs.GroupState;
import com.github.tobato.fastdfs.domain.fdfs.StorageNode;
import com.github.tobato.fastdfs.domain.fdfs.StorageNodeInfo;
import com.github.tobato.fastdfs.domain.fdfs.StorageState;
import com.github.tobato.fastdfs.service.TrackerClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile("local")
public class LocalFastDfsConfig {
    @Bean
    @Primary
    public TrackerClient localTrackerClient(
            @Qualifier("defaultTrackerClient") TrackerClient delegate,
            @Value("${match.fastdfs.storage-host:127.0.0.1}") String storageHost) {
        return new LocalTrackerClient(delegate, storageHost);
    }

    static class LocalTrackerClient implements TrackerClient {
        private final TrackerClient delegate;
        private final String storageHost;

        LocalTrackerClient(TrackerClient delegate, String storageHost) {
            this.delegate = delegate;
            this.storageHost = storageHost;
        }

        @Override
        public StorageNode getStoreStorage() {
            return local(delegate.getStoreStorage());
        }

        @Override
        public StorageNode getStoreStorage(String groupName) {
            return local(delegate.getStoreStorage(groupName));
        }

        @Override
        public StorageNodeInfo getFetchStorage(String groupName, String path) {
            return local(delegate.getFetchStorage(groupName, path));
        }

        @Override
        public StorageNodeInfo getUpdateStorage(String groupName, String path) {
            return local(delegate.getUpdateStorage(groupName, path));
        }

        @Override
        public List<GroupState> listGroups() {
            return delegate.listGroups();
        }

        @Override
        public List<StorageState> listStorages(String groupName) {
            return delegate.listStorages(groupName);
        }

        @Override
        public List<StorageState> listStorages(String groupName, String storageIpAddr) {
            return delegate.listStorages(groupName, storageIpAddr);
        }

        @Override
        public void deleteStorage(String groupName, String storageIpAddr) {
            delegate.deleteStorage(groupName, storageIpAddr);
        }

        private StorageNode local(StorageNode node) {
            if (node != null) {
                node.setIp(storageHost);
            }
            return node;
        }

        private StorageNodeInfo local(StorageNodeInfo node) {
            if (node != null) {
                node.setIp(storageHost);
            }
            return node;
        }
    }
}
