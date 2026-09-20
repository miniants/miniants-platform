package cn.miniants.platform.admin.support;

import cn.miniants.platform.data.query.EntityQuery;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

public final class EntityPages {

    private EntityPages() {
    }

    @SuppressWarnings("unchecked")
    public static <T> QueryWrapper<T> wrapper(Class<T> entityType, String filter, String order, String... exclude) {
        return (QueryWrapper<T>) EntityQuery.of(entityType).exclude(exclude).wrapper(filter, order);
    }
}
