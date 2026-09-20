package cn.miniants.platform.storage;

import io.minio.MinioClient;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

/**
 * 每个配好的后端注册一个 {@code ObjectStorage} bean，bean 名就是配置里的名字；
 * {@code primary} 那个标 primary，于是按类型注入拿到的是它。
 *
 * <p>没配 {@code platform.storage.backends} 就一个 bean 都不注册——只有部分进程需要存储，
 * 不该因为 classpath 上有 SDK 就强行连。
 */
@AutoConfiguration
@ConditionalOnClass(MinioClient.class)
@EnableConfigurationProperties(StorageProperties.class)
@Import(PlatformStorageAutoConfiguration.Registrar.class)
public class PlatformStorageAutoConfiguration {

    static class Registrar implements ImportBeanDefinitionRegistrar {

        private final StorageProperties properties;

        Registrar(Environment environment) {
            this.properties = Binder.get(environment)
                    .bind("platform.storage", StorageProperties.class)
                    .orElseGet(StorageProperties::new);
        }

        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry,
                BeanNameGenerator beanNameGenerator) {
            Map<String, StorageBackendProperties> backends = properties.getBackends();
            if (backends.isEmpty()) {
                return;
            }
            String primary = properties.getPrimary() != null
                    ? properties.getPrimary()
                    : backends.keySet().iterator().next();
            backends.forEach((name, backend) -> {
                RootBeanDefinition definition = new RootBeanDefinition(ObjectStorage.class,
                        () -> StorageBackends.create(backend));
                definition.setPrimary(name.equals(primary));
                registry.registerBeanDefinition(name, definition);
            });
        }
    }
}
