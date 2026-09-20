package cn.miniants.platform.ops;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 被包装组件的唯一生命周期所有者。
 *
 * <p>存在的理由：组件本身通常自带 {@code @PostConstruct} / {@code @PreDestroy}，再加一个
 * 运行时开关就变成两个主人，重复 start 或 destroy 两次的症状往往只是一条看不懂的
 * 连接异常。这里把 create / start / stop 收成三个回调，由本类独占调用。
 *
 * @param <T> 被包装的组件类型，本类不依赖它的任何 API
 */
public class ToggledComponent<T> implements RuntimeToggle, SmartInitializingSingleton, DisposableBean {

    private final String name;
    private final boolean configuredEnabled;
    private final CheckedSupplier<T> factory;
    private final CheckedConsumer<T> starter;
    private final CheckedConsumer<T> stopper;
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    private volatile T component;

    public ToggledComponent(String name,
                            boolean configuredEnabled,
                            CheckedSupplier<T> factory,
                            CheckedConsumer<T> starter,
                            CheckedConsumer<T> stopper) {
        this.name = name;
        this.configuredEnabled = configuredEnabled;
        this.factory = factory;
        this.starter = starter;
        this.stopper = stopper;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (configuredEnabled) {
            setRuntimeEnabled(true);
        }
    }

    @Override
    public void setRuntimeEnabled(boolean enabled) {
        lifecycleLock.lock();
        try {
            if (enabled) {
                enable();
            } else {
                disable();
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void enable() {
        if (component != null) {
            return;
        }
        T candidate = null;
        try {
            candidate = factory.get();
            starter.accept(candidate);
            component = candidate;
        } catch (Exception startFailure) {
            cleanupFailedStart(candidate, startFailure);
            throw new IllegalStateException(name + " 启动失败", startFailure);
        }
    }

    private void disable() {
        T current = component;
        if (current == null) {
            return;
        }
        try {
            stopper.accept(current);
            component = null;
        } catch (Exception stopFailure) {
            throw new IllegalStateException(name + " 停止失败", stopFailure);
        }
    }

    /** 起了一半失败的组件也要停，否则它可能已经占了端口或注册到了调度中心。 */
    private void cleanupFailedStart(T candidate, Exception startFailure) {
        if (candidate == null) {
            return;
        }
        try {
            stopper.accept(candidate);
        } catch (Exception cleanupFailure) {
            startFailure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public void destroy() {
        setRuntimeEnabled(false);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("configuredEnabled", configuredEnabled);
        state.put("runtimeEnabled", component != null);
        return state;
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {

        T get() throws Exception;
    }

    @FunctionalInterface
    public interface CheckedConsumer<T> {

        void accept(T value) throws Exception;
    }
}
