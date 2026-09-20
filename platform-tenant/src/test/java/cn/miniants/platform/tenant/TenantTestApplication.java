package cn.miniants.platform.tenant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.miniants.platform.tenant.support")
public class TenantTestApplication {
}
