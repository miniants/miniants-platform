package cn.miniants.platform.data.id;

import cn.miniants.platform.data.PlatformDataProperties;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 显式钉住雪花的机器位。
 *
 * <p>独立成一个自动装配，不并进 {@code PlatformDataAutoConfiguration}：已有项目会为了换掉
 * 内核的 {@code MetaObjectHandler} / 表前缀而整个 exclude 那一个（教务就是），并进去等于
 * 配了属性也不生效，而且不报错。
 */
@AutoConfiguration
@ConditionalOnClass(IdentifierGenerator.class)
@EnableConfigurationProperties(PlatformDataProperties.class)
public class PlatformSnowflakeAutoConfiguration {

    /** 机器位只有 5 位。 */
    private static final long MAX = 31L;

    /**
     * 不配 {@code worker-id} 就不注册，MyBatis-Plus 继续用它自己的 MAC/PID 推导——既有部署
     * 不受影响。
     */
    @Bean
    @ConditionalOnMissingBean(IdentifierGenerator.class)
    @ConditionalOnProperty(prefix = "platform.data.snowflake", name = "worker-id")
    public IdentifierGenerator platformIdentifierGenerator(PlatformDataProperties properties) {
        PlatformDataProperties.Snowflake snowflake = properties.getSnowflake();
        long workerId = checkRange("worker-id", snowflake.getWorkerId());
        long datacenterId = checkRange("datacenter-id",
                snowflake.getDatacenterId() == null ? 0L : snowflake.getDatacenterId());
        return new DefaultIdentifierGenerator(workerId, datacenterId);
    }

    /** 越界会被 MyBatis-Plus 截断成另一个编号而不是报错，那样冲突比不配还难查。 */
    private static long checkRange(String name, Long value) {
        if (value == null || value < 0 || value > MAX) {
            throw new IllegalStateException(
                    "platform.data.snowflake." + name + " 必须在 0–" + MAX + " 之间，当前为 " + value);
        }
        return value;
    }
}
