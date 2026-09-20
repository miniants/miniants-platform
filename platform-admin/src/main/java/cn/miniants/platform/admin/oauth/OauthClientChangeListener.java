package cn.miniants.platform.admin.oauth;

/**
 * OAuth 客户端管理变更扩展点。实现方可清理业务缓存或发布配置变更事件。
 */
@FunctionalInterface
public interface OauthClientChangeListener {

    void changed(String clientId);
}
