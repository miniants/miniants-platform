package cn.miniants.platform.storage;

import cn.miniants.platform.storage.minio.MinioObjectStorage;

/**
 * 按配置建后端。加新后端只改这里，不改调用方。
 */
public final class StorageBackends {

    private StorageBackends() {
    }

    public static ObjectStorage create(StorageBackendProperties properties) {
        return switch (properties.getBackend()) {
            case MINIO -> new MinioObjectStorage(properties);
        };
    }
}
