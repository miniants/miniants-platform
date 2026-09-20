package cn.miniants.platform.data.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;

import java.time.LocalDateTime;

/**
 * 未删除为 {@code NULL}，删除写库函数 {@code now()}。
 * 与部分存量表的 {@code Long deleted + value=false} 不是同一语义；采用方旧表不要直接改成这个基类。
 */
public class LogicDeleteEntity extends VersionedEntity {

    @TableLogic(value = "null", delval = "now()")
    protected LocalDateTime deleted;

    public LocalDateTime getDeleted() {
        return deleted;
    }

    public void setDeleted(LocalDateTime deleted) {
        this.deleted = deleted;
    }
}
