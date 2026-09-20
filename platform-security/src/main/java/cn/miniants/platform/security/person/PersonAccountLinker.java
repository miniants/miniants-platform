package cn.miniants.platform.security.person;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccountService;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

/**
 * 扩号后把返回的 username 回填 {@code sys_user.person_id}。缺 Bean 则跳过扩号。
 */
public final class PersonAccountLinker {

    private PersonAccountLinker() {
    }

    public static void expandAndLink(
            ObjectProvider<PersonExpansionSource> expansionSource,
            UserAccountService userAccountService,
            Person person,
            String idDocType,
            String rawIdNo) {
        PersonExpansionSource source = expansionSource.getIfAvailable();
        if (source == null || person == null || person.id() == null) {
            return;
        }
        List<PrincipalHint> hints = source.expand(person, idDocType, rawIdNo);
        if (hints == null || hints.isEmpty()) {
            return;
        }
        for (PrincipalHint hint : hints) {
            if (hint == null || hint.username() == null || hint.username().isBlank()) {
                continue;
            }
            UserAccount account = userAccountService.findByUsername(hint.username().trim())
                    .orElseThrow(() -> new PlatformException("扩号账号不存在: " + hint.username()));
            if (!person.id().equals(account.personId())) {
                userAccountService.updatePersonId(account.id(), person.id());
            }
        }
    }
}
