package cn.miniants.platform.storage;

import cn.miniants.platform.storage.minio.MinioObjectStorage;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformStorageAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformStorageAutoConfiguration.class));

    /** IoT 之类的进程 classpath 上有 SDK 但没配存储，不该因此启动失败。 */
    @Test
    void withoutConfiguredBackendsNothingIsRegistered() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ObjectStorage.class));
    }

    @Test
    void eachBackendBecomesABeanNamedAfterItsKey() {
        runner.withPropertyValues(
                        "platform.storage.backends.photos.endpoint=http://localhost:9000",
                        "platform.storage.backends.photos.access-key=k",
                        "platform.storage.backends.photos.secret-key=s",
                        "platform.storage.backends.photos.bucket-name=photos",
                        "platform.storage.backends.docs.endpoint=http://localhost:9000",
                        "platform.storage.backends.docs.access-key=k",
                        "platform.storage.backends.docs.secret-key=s",
                        "platform.storage.backends.docs.bucket-name=docs")
                .run(context -> {
                    assertThat(context).hasBean("photos").hasBean("docs");
                    assertThat(context.getBean("docs", ObjectStorage.class).bucketName()).isEqualTo("docs");
                });
    }

    @Test
    void firstDeclaredBackendIsInjectedByType() {
        runner.withPropertyValues(
                        "platform.storage.backends.photos.endpoint=http://localhost:9000",
                        "platform.storage.backends.photos.access-key=k",
                        "platform.storage.backends.photos.secret-key=s",
                        "platform.storage.backends.photos.bucket-name=photos",
                        "platform.storage.backends.docs.endpoint=http://localhost:9000",
                        "platform.storage.backends.docs.access-key=k",
                        "platform.storage.backends.docs.secret-key=s",
                        "platform.storage.backends.docs.bucket-name=docs")
                .run(context -> assertThat(context.getBean(ObjectStorage.class).bucketName())
                        .isEqualTo("photos"));
    }

    @Test
    void primaryOverridesDeclarationOrder() {
        runner.withPropertyValues(
                        "platform.storage.primary=docs",
                        "platform.storage.backends.photos.endpoint=http://localhost:9000",
                        "platform.storage.backends.photos.access-key=k",
                        "platform.storage.backends.photos.secret-key=s",
                        "platform.storage.backends.photos.bucket-name=photos",
                        "platform.storage.backends.docs.endpoint=http://localhost:9000",
                        "platform.storage.backends.docs.access-key=k",
                        "platform.storage.backends.docs.secret-key=s",
                        "platform.storage.backends.docs.bucket-name=docs")
                .run(context -> assertThat(context.getBean(ObjectStorage.class).bucketName())
                        .isEqualTo("docs"));
    }

    @Test
    void minioClientIsReachableForBulkOperationsTheInterfaceDoesNotCover() {
        runner.withPropertyValues(
                        "platform.storage.backends.photos.endpoint=http://localhost:9000",
                        "platform.storage.backends.photos.access-key=k",
                        "platform.storage.backends.photos.secret-key=s",
                        "platform.storage.backends.photos.bucket-name=photos")
                .run(context -> {
                    ObjectStorage storage = context.getBean(ObjectStorage.class);
                    assertThat(storage).isInstanceOf(MinioObjectStorage.class);
                    assertThat(storage.unwrap(MinioClient.class)).isNotNull();
                });
    }
}
