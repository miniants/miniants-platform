package cn.miniants.platform.storage.minio;

import cn.miniants.platform.storage.ObjectStat;
import cn.miniants.platform.storage.ObjectStorage;
import cn.miniants.platform.storage.StorageBackendProperties;
import cn.miniants.platform.storage.StoredObject;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class MinioObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);

    /** 长度未知时交给 minio 分片，不能用 {@code available()} 猜。 */
    private static final long UNKNOWN_PART_SIZE = -1L;

    private final MinioClient client;
    private final String defaultBucketName;
    private final String bucketName;

    public MinioObjectStorage(StorageBackendProperties properties) {
        this(MinioClient.builder()
                        .endpoint(properties.getEndpoint())
                        .credentials(properties.getAccessKey(), properties.getSecretKey())
                        .build(),
                properties.getBucketName(),
                properties.getBucketName());
    }

    private MinioObjectStorage(MinioClient client, String defaultBucketName, String bucketName) {
        this.client = client;
        this.defaultBucketName = defaultBucketName;
        this.bucketName = bucketName == null || bucketName.isBlank() ? defaultBucketName : bucketName;
    }

    @Override
    public ObjectStorage bucket(String bucketName) {
        if (bucketName == null || bucketName.isBlank() || bucketName.equals(this.bucketName)) {
            return this;
        }
        return new MinioObjectStorage(client, defaultBucketName, bucketName);
    }

    @Override
    public String bucketName() {
        return bucketName;
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        return type.isInstance(client) ? type.cast(client) : ObjectStorage.super.unwrap(type);
    }

    @Override
    public ObjectStat stat(String objectName) {
        try {
            StatObjectResponse response = client.statObject(StatObjectArgs.builder()
                    .bucket(bucketName).object(objectName).build());
            return new ObjectStat(bucketName, objectName, response.size(), response.contentType(),
                    response.lastModified() == null ? null : response.lastModified().toInstant());
        } catch (Exception e) {
            throw new IllegalStateException("读取文件信息失败", e);
        }
    }

    @Override
    public boolean exists(String objectName) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
            return true;
        } catch (ErrorResponseException e) {
            // 缺对象是正常分支，只有其它错误才算故障
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new IllegalStateException("读取文件信息失败", e);
        } catch (Exception e) {
            throw new IllegalStateException("读取文件信息失败", e);
        }
    }

    @Override
    public InputStream download(String objectName) {
        try {
            if (!exists(objectName)) {
                return null;
            }
            return client.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
        } catch (Exception e) {
            log.error("MinIO 下载失败 bucket={} object={}", bucketName, objectName, e);
            throw new IllegalStateException("读取文件失败");
        }
    }

    @Override
    public boolean delete(String objectName) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("删除文件失败", e);
        }
    }

    @Override
    public StoredObject upload(InputStream source, long length, String objectName) {
        if (source == null || objectName == null) {
            return null;
        }
        try {
            ObjectWriteResponse response = client.putObject(PutObjectArgs.builder()
                    .bucket(bucketName).object(objectName)
                    .stream(source, length, UNKNOWN_PART_SIZE).build());
            return response == null ? null
                    : new StoredObject(bucketName, objectName, response.versionId());
        } catch (Exception e) {
            throw new IllegalStateException("上传文件失败", e);
        }
    }
}
