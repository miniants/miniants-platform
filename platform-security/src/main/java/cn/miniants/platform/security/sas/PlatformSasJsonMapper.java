package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.account.UserAccount;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * SAS JDBC 授权持久化用的 JsonMapper：在 Spring Security 默认白名单上追加平台 {@link UserAccount}。
 */
final class PlatformSasJsonMapper {

    private PlatformSasJsonMapper() {
    }

    static JsonMapper create() {
        ClassLoader loader = PlatformSasJsonMapper.class.getClassLoader();
        BasicPolymorphicTypeValidator.Builder ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(UserAccount.class)
                .allowIfSubType(Long.class)
                .allowIfSubType(Integer.class)
                .allowIfSubType(Boolean.class)
                .allowIfSubType("java.util.ImmutableCollections");
        List<JacksonModule> modules = SecurityJacksonModules.getModules(loader, ptv);
        return JsonMapper.builder()
                .addModules(modules)
                .build();
    }
}
