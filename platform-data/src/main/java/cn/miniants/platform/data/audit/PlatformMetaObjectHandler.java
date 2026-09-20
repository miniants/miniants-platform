package cn.miniants.platform.data.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

public class PlatformMetaObjectHandler implements MetaObjectHandler {

    private final AuditorSupplier auditorSupplier;

    public PlatformMetaObjectHandler(AuditorSupplier auditorSupplier) {
        this.auditorSupplier = auditorSupplier;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        Auditor auditor = auditorSupplier.current().orElseGet(Auditor::system);
        fillHasGetter(metaObject, "createId", auditor.id());
        fillHasGetter(metaObject, "createBy", auditor.name());
        fillHasGetter(metaObject, "createTime", LocalDateTime.now());
        fillHasGetter(metaObject, "version", 1L);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Auditor auditor = auditorSupplier.current().orElseGet(Auditor::system);
        fillHasGetter(metaObject, "updateBy", auditor.name());
        fillHasGetter(metaObject, "updateTime", LocalDateTime.now());
    }

    private void fillHasGetter(MetaObject metaObject, String fieldName, Object fieldVal) {
        if (metaObject.hasGetter(fieldName)) {
            this.fillStrategy(metaObject, fieldName, fieldVal);
        }
    }
}
