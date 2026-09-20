package cn.miniants.platform.data.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.function.Supplier;

/**
 * 唯一性校验。
 *
 * <p>抛 {@link IllegalArgumentException} 而不是 {@code PlatformException}：
 * 前者由 {@code PlatformWebAdvice} 落成 HTTP 400，后者落成 HTTP 200 带业务码。
 * 「这个账号已经有人用了」属于请求本身不对，应该是 400。
 */
public interface BaseService<T> extends IService<T> {

    /**
     * @param condition 为假时跳过校验。用于「只有改了这个字段才需要查重」
     * @param message   已存在时的提示
     */
    default void checkExists(boolean condition, Supplier<LambdaQueryWrapper<T>> supplier, String message) {
        if (condition) {
            checkExists(supplier.get(), message);
        }
    }

    default void checkExists(LambdaQueryWrapper<T> lqw, String message) {
        if (getOne(lqw.last("limit 1")) != null) {
            throw new IllegalArgumentException(message);
        }
    }
}
