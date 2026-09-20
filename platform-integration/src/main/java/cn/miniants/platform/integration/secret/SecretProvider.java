package cn.miniants.platform.integration.secret;

import cn.miniants.platform.core.error.PlatformException;

import java.util.Optional;

/**
 * 密钥只通过本接口读取。实现不得把值写进日志。
 */
public interface SecretProvider {

    Optional<String> find(String name);

    default String require(String name) {
        return find(name)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new PlatformException("未配置密钥"));
    }
}
