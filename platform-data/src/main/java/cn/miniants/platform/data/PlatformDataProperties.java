package cn.miniants.platform.data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.data")
public class PlatformDataProperties {

    /**
     * MyBatis-Plus：未写 {@code @TableName} 时拼到类名下划线之前；空串表示不加前缀。
     * 已写 {@code @TableName} 的表名不再加此前缀。
     * <p>JDBC 内核表（{@link #jdbcTablePrefix()}）在 null/空串时仍回落到 {@code sys_}，
     * 以便教务 dest 清空 MP 前缀、实体写死 {@code sys_*} 后，JDBC 仍命中真实表名。
     */
    private String tablePrefix = "sys_";

    private final Snowflake snowflake = new Snowflake();

    /**
     * 雪花 ID 的机器位。MyBatis-Plus 默认从网卡 MAC 推 {@code datacenterId}、从
     * {@code datacenterId + JVM 进程 PID} 推 {@code workerId}——同一台机器上跑克隆容器、
     * 或从同一模板克隆出的虚机（MAC 相同）撞上 PID，就会生成重复 ID。
     *
     * <p>要么所有实例都配，要么都不配。只配一部分反而更糟：没配的那些仍走 MAC/PID
     * 推导，可能正好推出已被占用的编号，而这种冲突只会表现为某次插入 duplicate key，
     * 从日志里回溯不到是哪两个实例撞的。
     */
    public static class Snowflake {

        /**
         * 5 位，取值 0–31。留空即沿用 MyBatis-Plus 的 MAC/PID 推导（保持既有行为）。
         */
        private Long workerId;

        /** 5 位，取值 0–31。配了 {@code workerId} 但留空本项时按 0 处理。 */
        private Long datacenterId;

        public Long getWorkerId() {
            return workerId;
        }

        public void setWorkerId(Long workerId) {
            this.workerId = workerId;
        }

        public Long getDatacenterId() {
            return datacenterId;
        }

        public void setDatacenterId(Long datacenterId) {
            this.datacenterId = datacenterId;
        }
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    /**
     * 供 platform-admin JDBC 拼表名。null / blank → {@code sys_}。
     */
    public String jdbcTablePrefix() {
        return tablePrefix == null || tablePrefix.isBlank() ? "sys_" : tablePrefix;
    }

    public Snowflake getSnowflake() {
        return snowflake;
    }
}
