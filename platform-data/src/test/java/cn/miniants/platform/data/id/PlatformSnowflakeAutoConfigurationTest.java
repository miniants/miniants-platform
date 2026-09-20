package cn.miniants.platform.data.id;

import cn.miniants.platform.data.PlatformDataAutoConfiguration;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 雪花机器位的显式配置。
 *
 * <p>断言落在「生成出来的 ID 里机器位是不是配的那个值」而不是「bean 在不在」：
 * 注册了 bean 但没被 MyBatis-Plus 采纳，症状和没配一模一样。
 */
class PlatformSnowflakeAutoConfigurationTest {

    /** 位序：timestamp(41) | datacenterId(5) | workerId(5) | sequence(12) */
    private static final int WORKER_ID_SHIFT = 12;
    private static final int DATACENTER_ID_SHIFT = 17;
    private static final long FIVE_BIT_MASK = 0x1FL;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformSnowflakeAutoConfiguration.class));

    /** 既有部署没配过这两项，注册了反而会换一套机器位。 */
    @Test
    void withoutAWorkerIdNothingIsRegistered() {
        runner.run(context -> assertThat(context).doesNotHaveBean(IdentifierGenerator.class));
    }

    @Test
    void configuredMachineBitsLandInTheGeneratedId() {
        runner.withPropertyValues(
                        "platform.data.snowflake.worker-id=7",
                        "platform.data.snowflake.datacenter-id=3")
                .run(context -> {
                    long id = context.getBean(IdentifierGenerator.class).nextId(new Object()).longValue();
                    assertThat(workerIdOf(id)).isEqualTo(7L);
                    assertThat(datacenterIdOf(id)).isEqualTo(3L);
                });
    }

    /** 大多数部署只需要区分实例，不需要机房维度。 */
    @Test
    void datacenterIdDefaultsToZero() {
        runner.withPropertyValues("platform.data.snowflake.worker-id=5")
                .run(context -> {
                    long id = context.getBean(IdentifierGenerator.class).nextId(new Object()).longValue();
                    assertThat(workerIdOf(id)).isEqualTo(5L);
                    assertThat(datacenterIdOf(id)).isZero();
                });
    }

    /** 机器位只有 5 位。32 会被截成 0，悄悄和另一个实例撞上，比不配更难查。 */
    @Test
    void outOfRangeWorkerIdFailsStartupInsteadOfWrappingAround() {
        runner.withPropertyValues("platform.data.snowflake.worker-id=32")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void outOfRangeDatacenterIdFailsStartup() {
        runner.withPropertyValues(
                        "platform.data.snowflake.worker-id=1",
                        "platform.data.snowflake.datacenter-id=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void applicationSuppliedGeneratorWins() {
        IdentifierGenerator custom = new DefaultIdentifierGenerator(9L, 0L);
        runner.withPropertyValues("platform.data.snowflake.worker-id=7")
                .withBean(IdentifierGenerator.class, () -> custom)
                .run(context -> assertThat(context.getBean(IdentifierGenerator.class)).isSameAs(custom));
    }

    /**
     * 已有项目会为了换掉内核的 MetaObjectHandler / 表前缀而整个 exclude
     * {@code PlatformDataAutoConfiguration}（教务就是这么干的）。机器位必须活下来，
     * 否则配了属性也不生效且不报错。
     */
    @Test
    void survivesExcludingTheMainDataAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PlatformDataAutoConfiguration.class, PlatformSnowflakeAutoConfiguration.class))
                .withPropertyValues(
                        "spring.autoconfigure.exclude=cn.miniants.platform.data.PlatformDataAutoConfiguration",
                        "platform.data.snowflake.worker-id=11")
                .run(context -> {
                    long id = context.getBean(IdentifierGenerator.class).nextId(new Object()).longValue();
                    assertThat(workerIdOf(id)).isEqualTo(11L);
                });
    }

    private static long workerIdOf(long id) {
        return (id >>> WORKER_ID_SHIFT) & FIVE_BIT_MASK;
    }

    private static long datacenterIdOf(long id) {
        return (id >>> DATACENTER_ID_SHIFT) & FIVE_BIT_MASK;
    }
}
