package cn.miniants.platform.data.scope;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/**
 * 启动时确认 DataScope 拦截器已挂入 MybatisPlusInterceptor 链。
 */
@ConditionalOnBean(DataScopeInnerInterceptor.class)
public class DataScopeAssemblyChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataScopeAssemblyChecker.class);

    private final DataScopeInnerInterceptor dataScopeInnerInterceptor;
    private final MybatisPlusInterceptor mybatisPlusInterceptor;

    public DataScopeAssemblyChecker(
            DataScopeInnerInterceptor dataScopeInnerInterceptor,
            MybatisPlusInterceptor mybatisPlusInterceptor) {
        this.dataScopeInnerInterceptor = dataScopeInnerInterceptor;
        this.mybatisPlusInterceptor = mybatisPlusInterceptor;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean wired = mybatisPlusInterceptor.getInterceptors().stream()
                .anyMatch(i -> i == dataScopeInnerInterceptor);
        if (!wired) {
            log.error("[pl] DataScopeInnerInterceptor Bean 存在但未挂入 MybatisPlusInterceptor；DataScope 将静默失效");
        }
    }
}
