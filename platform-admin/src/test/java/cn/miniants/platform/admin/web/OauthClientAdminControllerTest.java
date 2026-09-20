package cn.miniants.platform.admin.web;

import cn.miniants.platform.admin.oauth.OauthCatalog;
import cn.miniants.platform.admin.service.OauthClientAdminService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class OauthClientAdminControllerTest {

    @Test
    void mergesCatalogBeansInOrderAndRemovesDuplicatesAndBlanks() {
        OauthCatalog first = new OauthCatalog() {
            @Override
            public List<String> grantTypes() {
                return List.of("authorization_code", " client_credentials ");
            }

            @Override
            public List<String> scopes() {
                return List.of("openid", "profile");
            }
        };
        OauthCatalog second = new OauthCatalog() {
            @Override
            public List<String> grantTypes() {
                return List.of("client_credentials", "refresh_token");
            }

            @Override
            public List<String> scopes() {
                return List.of("profile", "email");
            }
        };
        OauthClientAdminController controller = new OauthClientAdminController(
                mock(OauthClientAdminService.class), List.of(first, second));

        assertEquals(List.of("authorization_code", "client_credentials", "refresh_token"),
                controller.grantTypes());
        assertEquals(List.of("openid", "profile", "email"), controller.scopes());
    }

    @Test
    void defaultsToEmptyCatalog() {
        OauthClientAdminController controller = new OauthClientAdminController(
                mock(OauthClientAdminService.class), List.of());

        assertEquals(List.of(), controller.grantTypes());
        assertEquals(List.of(), controller.scopes());
    }
}
