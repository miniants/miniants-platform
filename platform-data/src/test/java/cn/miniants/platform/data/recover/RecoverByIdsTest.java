package cn.miniants.platform.data.recover;

import cn.miniants.platform.data.mapper.CrudMapper;
import cn.miniants.platform.data.mapper.PlatformSqlInjectorAutoConfiguration;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code recoverByIds} 是拼出来的 SQL，编译期发现不了任何问题。
 *
 * <p>两种逻辑删除列都要跑：内核 {@code LogicDeleteEntity} 用 {@code TIMESTAMP}（未删为
 * {@code NULL}），现网不少表用数值列。{@code IS NOT NULL} 那条分支是本次新增的——老实现
 * 写死 {@code deleted > 0} 且写死主键叫 {@code id}，换成时间戳列一行也匹配不到。
 */
class RecoverByIdsTest {

    /** 每个用例独立的库，免得建表语句互相撞。 */
    private static ApplicationContextRunner runnerOn(String database) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        MybatisPlusAutoConfiguration.class,
                        PlatformSqlInjectorAutoConfiguration.class))
                .withUserConfiguration(MapperConfig.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:" + database + ";MODE=MySQL",
                        "spring.datasource.driver-class-name=org.h2.Driver");
    }

    @Test
    void recoversRowsWhoseTimestampColumnIsSet() {
        runnerOn("recover_stamped").run(context -> {
            JdbcTemplate jdbc = jdbcOf(context.getBean(DataSource.class));
            jdbc.execute("CREATE TABLE stamped (id BIGINT PRIMARY KEY, deleted TIMESTAMP NULL)");
            jdbc.update("INSERT INTO stamped VALUES (1, NULL), (2, CURRENT_TIMESTAMP), (3, CURRENT_TIMESTAMP)");

            int affected = context.getBean(StampedMapper.class).recoverByIds(List.of(2L, 3L));

            assertThat(affected).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM stamped WHERE deleted IS NULL", Integer.class)).isEqualTo(3);
        });
    }

    /** 从没删过的行不该计入，否则调用方拿到的「恢复了几条」是假的。 */
    @Test
    void leavesRowsThatWereNeverDeleted() {
        runnerOn("recover_untouched").run(context -> {
            JdbcTemplate jdbc = jdbcOf(context.getBean(DataSource.class));
            jdbc.execute("CREATE TABLE stamped (id BIGINT PRIMARY KEY, deleted TIMESTAMP NULL)");
            jdbc.update("INSERT INTO stamped VALUES (1, NULL)");

            assertThat(context.getBean(StampedMapper.class).recoverByIds(List.of(1L))).isZero();
        });
    }

    /** 现网形状：数值列 + 主键不叫 {@code id}。老实现把两者都写死了。 */
    @Test
    void recoversNumericColumnKeyedByANonIdPrimaryKey() {
        runnerOn("recover_numbered").run(context -> {
            JdbcTemplate jdbc = jdbcOf(context.getBean(DataSource.class));
            jdbc.execute("CREATE TABLE numbered (sample_no BIGINT PRIMARY KEY, removed BIGINT NOT NULL)");
            jdbc.update("INSERT INTO numbered VALUES (1, 0), (2, 20260823120000)");

            int affected = context.getBean(NumberedMapper.class).recoverByIds(List.of(1L, 2L));

            assertThat(affected).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM numbered WHERE removed = 0", Integer.class)).isEqualTo(2);
        });
    }

    /** 没有逻辑删除列的表不该拿到这个方法，否则会拼出一条恢复不了任何东西的 UPDATE。 */
    @Test
    void tablesWithoutLogicDeleteGetNoStatement() {
        runnerOn("recover_plain").run(context -> assertThat(context.getBean(SqlSessionFactory.class)
                .getConfiguration()
                .hasStatement(PlainMapper.class.getName() + ".recoverByIds")).isFalse());
    }

    private static JdbcTemplate jdbcOf(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Configuration(proxyBeanMethods = false)
    @MapperScan("cn.miniants.platform.data.recover")
    static class MapperConfig {
    }

    @TableName("stamped")
    static class Stamped {

        @TableId(type = IdType.INPUT)
        private Long id;

        @TableLogic(value = "null", delval = "now()")
        private LocalDateTime deleted;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public LocalDateTime getDeleted() {
            return deleted;
        }

        public void setDeleted(LocalDateTime deleted) {
            this.deleted = deleted;
        }
    }

    @TableName("numbered")
    static class Numbered {

        @TableId(value = "sample_no", type = IdType.INPUT)
        private Long sampleNo;

        @TableLogic(value = "0", delval = "now()")
        private Long removed;

        public Long getSampleNo() {
            return sampleNo;
        }

        public void setSampleNo(Long sampleNo) {
            this.sampleNo = sampleNo;
        }

        public Long getRemoved() {
            return removed;
        }

        public void setRemoved(Long removed) {
            this.removed = removed;
        }
    }

    @TableName("plain")
    static class Plain {

        @TableId(type = IdType.INPUT)
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    interface StampedMapper extends CrudMapper<Stamped> {
    }

    interface NumberedMapper extends CrudMapper<Numbered> {
    }

    interface PlainMapper extends CrudMapper<Plain> {
    }
}
