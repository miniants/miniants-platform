package cn.miniants.platform.data.entity;

import cn.miniants.platform.data.validation.Create;
import cn.miniants.platform.data.validation.Update;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

public class SuperEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @Null(groups = Create.class)
    @NotNull(groups = Update.class)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
