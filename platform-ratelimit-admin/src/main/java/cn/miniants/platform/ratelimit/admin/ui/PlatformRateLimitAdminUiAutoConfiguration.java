package cn.miniants.platform.ratelimit.admin.ui;

import cn.miniants.platform.ratelimit.admin.RateLimitAdminProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 可选管理控制台：只挂载专属路径，不接管应用根路径。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "platform.ratelimit.admin", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "platform.ratelimit.admin.ui", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RateLimitAdminProperties.class)
public class PlatformRateLimitAdminUiAutoConfiguration {

    static final String CLASSPATH = "classpath:/META-INF/resources/platform-ratelimit-ui/";

    @Bean
    public WebMvcConfigurer rateLimitAdminUiWebMvcConfigurer(RateLimitAdminProperties properties) {
        String mount = normalize(properties.getUi().getPath());
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler(mount + "/**")
                        .addResourceLocations(CLASSPATH)
                        .resourceChain(true)
                        .addResolver(new PathResourceResolver() {
                            @Override
                            protected Resource getResource(String resourcePath, Resource location) throws IOException {
                                Resource resource = super.getResource(resourcePath, location);
                                if (resource != null && resource.exists() && resource.isReadable()) {
                                    return resource;
                                }
                                Resource index = new ClassPathResource("META-INF/resources/platform-ratelimit-ui/index.html");
                                return index.exists() ? index : null;
                            }
                        });
            }

            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addRedirectViewController(mount, mount + "/");
                registry.addViewController(mount + "/").setViewName("forward:" + mount + "/index.html");
            }
        };
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/platform/ratelimit";
        }
        String trimmed = path.startsWith("/") ? path : "/" + path;
        if (trimmed.endsWith("/") && trimmed.length() > 1) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
