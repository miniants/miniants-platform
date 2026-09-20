package cn.miniants.platform.demo;

import cn.miniants.platform.security.Permission;
import cn.miniants.platform.security.PublicAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    @PublicAccess
    @GetMapping("/healthz")
    public String healthz() {
        return "ok";
    }

    @PublicAccess
    @GetMapping("/__demo/boom")
    public String boom() {
        throw new IllegalStateException("SQL syntax error near 'secret_table'");
    }

    @Permission("demo:ping")
    @GetMapping("/__demo/secure")
    public String secure() {
        return "pong";
    }
}
