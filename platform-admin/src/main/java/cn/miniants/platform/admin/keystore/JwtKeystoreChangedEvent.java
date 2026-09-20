package cn.miniants.platform.admin.keystore;

import org.springframework.context.ApplicationEvent;

/**
 * 本实例写库并 {@code reload()} 之后发布，供多实例传播订阅。
 *
 * <p>TODO(multi-instance)：后续用 Redis pub/sub（channel 带校区前缀）转发给其它 boot 实例，
 * 收到后调用 {@code RotatableJwkSource.reload()}。本层不依赖 Redis；缺通知时未刷新实例需重启。
 */
public class JwtKeystoreChangedEvent extends ApplicationEvent {

    public static final String ACTION_GENERATE = "generate";
    public static final String ACTION_ACTIVATE = "activate";
    public static final String ACTION_RETIRE = "retire";

    private final String kid;
    private final String action;

    public JwtKeystoreChangedEvent(Object source, String kid, String action) {
        super(source);
        this.kid = kid;
        this.action = action;
    }

    public String kid() {
        return kid;
    }

    public String action() {
        return action;
    }
}
