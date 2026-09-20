package cn.miniants.platform.ratelimit.observe;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * 只读 Actuator：backend、revision、快照年龄、绑定策略编码。
 */
@Endpoint(id = "ratelimit")
public class RateLimitEndpoint {

    private final RateLimitRuntimeInspector inspector;

    public RateLimitEndpoint(RateLimitRuntimeInspector inspector) {
        this.inspector = inspector;
    }

    @ReadOperation
    public RateLimitRuntimeSnapshot info() {
        return inspector.snapshot();
    }
}
