package cn.miniants.platform.data.query;

import com.baomidou.mybatisplus.annotation.TableField;

import java.time.LocalDateTime;

/**
 * 对齐现网黄金用例字段；password / extra 用于验证拒绝。
 */
class QuerySample {

    private Long id;
    private String realName;
    private Long academyId;
    private Integer status;
    private Integer score;
    private Long deleted;
    private String remark;
    private LocalDateTime createTime;

    @QueryHidden
    private String password;

    @TableField(exist = false)
    private String extra;
}
