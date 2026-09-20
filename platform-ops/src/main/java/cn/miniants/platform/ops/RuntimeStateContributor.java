package cn.miniants.platform.ops;

import java.util.Map;

/**
 * 服务专用运行时状态。实现不得在读取状态时产生副作用。
 */
public interface RuntimeStateContributor {

    String getName();

    Map<String, Object> getState();

    default boolean supportsDrain() {
        return false;
    }

    default void setDraining(boolean draining) {
        throw new UnsupportedOperationException("不支持排空");
    }
}
