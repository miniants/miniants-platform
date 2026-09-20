package cn.miniants.platform.security.person;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.account.InMemoryUserAccountService;
import cn.miniants.platform.security.account.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonAccountLinkerTest {

    @Test
    void linksHintUsernamesToPerson() {
        InMemoryUserAccountService accounts = new InMemoryUserAccountService()
                .add(new UserAccount(1L, "stu001", "x", "学生", true, false, List.of(), null));
        Person person = new Person(99L, "张三", "id_card", "digest", null);
        PersonExpansionSource expansion = new PersonExpansionSource() {
            @Override
            public List<PrincipalHint> expand(Person p) {
                return List.of();
            }

            @Override
            public List<PrincipalHint> expand(Person p, String idDocType, String rawIdNo) {
                return List.of(new PrincipalHint("stu001", "学生", false));
            }
        };

        PersonAccountLinker.expandAndLink(provider(expansion), accounts, person, "id_card", "110101199001011234");

        assertThat(accounts.findByUsername("stu001")).get()
                .extracting(UserAccount::personId)
                .isEqualTo(99L);
    }

    @Test
    void missingAccountFails() {
        InMemoryUserAccountService accounts = new InMemoryUserAccountService();
        Person person = new Person(99L, "张三", "id_card", "digest", null);
        PersonExpansionSource expansion = new PersonExpansionSource() {
            @Override
            public List<PrincipalHint> expand(Person p) {
                return List.of();
            }

            @Override
            public List<PrincipalHint> expand(Person p, String idDocType, String rawIdNo) {
                return List.of(new PrincipalHint("missing", "x", false));
            }
        };

        assertThatThrownBy(() -> PersonAccountLinker.expandAndLink(
                provider(expansion), accounts, person, "id_card", "110101199001011234"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("扩号账号不存在");
    }

    private static ObjectProvider<PersonExpansionSource> provider(PersonExpansionSource source) {
        return new ObjectProvider<>() {
            @Override
            public PersonExpansionSource getObject() {
                return source;
            }

            @Override
            public PersonExpansionSource getObject(Object... args) {
                return source;
            }

            @Override
            public PersonExpansionSource getIfAvailable() {
                return source;
            }

            @Override
            public PersonExpansionSource getIfUnique() {
                return source;
            }
        };
    }
}
