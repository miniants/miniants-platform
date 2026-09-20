package cn.miniants.platform.admin.oauth;

import java.util.Collection;
import java.util.List;

/**
 * OAuth 管理端可选值目录 SPI。业务应用可提供多个 Bean，平台会合并、去重后暴露。
 */
public interface OauthCatalog {

    default Collection<String> grantTypes() {
        return List.of();
    }

    default Collection<String> scopes() {
        return List.of();
    }
}
