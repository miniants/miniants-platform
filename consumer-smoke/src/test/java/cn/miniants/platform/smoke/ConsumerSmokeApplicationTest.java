package cn.miniants.platform.smoke;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class ConsumerSmokeApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsFromPublishedBom() {
        assertNotNull(context);
    }
}
