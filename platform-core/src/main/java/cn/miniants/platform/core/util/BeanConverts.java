package cn.miniants.platform.core.util;

import cn.miniants.platform.core.error.PlatformException;
import org.springframework.beans.BeanUtils;

/**
 * 对象属性拷贝。
 */
public final class BeanConverts {

    private BeanConverts() {
    }

    public static <T> T copy(Object source, Class<T> clazz) {
        try {
            T t = clazz.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, t);
            return t;
        } catch (Exception e) {
            throw new PlatformException("转换对象失败", e);
        }
    }
}
