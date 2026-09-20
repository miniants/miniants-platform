package cn.miniants.platform.security.bff;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.AccessAuth;
import cn.miniants.platform.security.LoginAttempt;
import cn.miniants.platform.security.LoginAudits;
import cn.miniants.platform.security.PublicAccess;
import cn.miniants.platform.security.SecurityAuditSink;
import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccountService;
import cn.miniants.platform.security.crypto.IdCredentialHasher;
import cn.miniants.platform.security.identity.ExternalIdentity;
import cn.miniants.platform.security.identity.ExternalIdentityBinding;
import cn.miniants.platform.security.identity.ExternalIdentityProviderRegistry;
import cn.miniants.platform.security.person.Person;
import cn.miniants.platform.security.person.PersonAccountLinker;
import cn.miniants.platform.security.person.PersonExpansionSource;
import cn.miniants.platform.security.person.PersonRegistry;
import cn.miniants.platform.security.person.PrincipalDirectory;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@PublicAccess
@RequestMapping("/auth/open/id-no")
public class BffRealNameController {

    private static final Logger log = LoggerFactory.getLogger(BffRealNameController.class);
    private static final String OP = "身份证实名登录";

    private final PlatformBffProperties bffProperties;
    private final ExternalIdentityProviderRegistry providerRegistry;
    private final IdCredentialHasher idCredentialHasher;
    private final PersonRegistry personRegistry;
    private final ExternalIdentityBinding binding;
    private final UserAccountService userAccountService;
    private final ObjectProvider<PrincipalDirectory> principalDirectory;
    private final ObjectProvider<PersonExpansionSource> expansionSource;
    private final UserTokenIssuer tokenIssuer;
    private final SecurityAuditSink auditSink;

    public BffRealNameController(
            PlatformBffProperties bffProperties,
            ExternalIdentityProviderRegistry providerRegistry,
            IdCredentialHasher idCredentialHasher,
            PersonRegistry personRegistry,
            ExternalIdentityBinding binding,
            UserAccountService userAccountService,
            ObjectProvider<PrincipalDirectory> principalDirectory,
            ObjectProvider<PersonExpansionSource> expansionSource,
            UserTokenIssuer tokenIssuer,
            SecurityAuditSink auditSink) {
        this.bffProperties = bffProperties;
        this.providerRegistry = providerRegistry;
        this.idCredentialHasher = idCredentialHasher;
        this.personRegistry = personRegistry;
        this.binding = binding;
        this.userAccountService = userAccountService;
        this.principalDirectory = principalDirectory;
        this.expansionSource = expansionSource;
        this.tokenIssuer = tokenIssuer;
        this.auditSink = auditSink;
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> bindAndIssue(
            @RequestParam(value = "wx_code", required = false) String wxCode,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam("idNo") String idNo,
            @RequestParam("idName") String idName,
            @RequestParam(value = "designated_username", required = false) String designatedUsername,
            @RequestParam(value = "client_id", required = false) String clientId,
            HttpServletRequest request) {
        AccessAuth.stampOp(request, OP);
        String username = null;
        try {
            if (idNo == null || idNo.isBlank() || idName == null || idName.isBlank()) {
                throw new PlatformException("实名信息不完整");
            }
            Map<String, String> credentials = new HashMap<>();
            if (wxCode != null) {
                credentials.put("wx_code", wxCode);
            }
            if (code != null) {
                credentials.put("code", code);
            }
            ExternalIdentity resolved = providerRegistry.resolveConfigured(null).resolve(credentials);
            String trimmedIdNo = idNo.trim();
            String digest = idCredentialHasher.digest("id_card", trimmedIdNo);
            Person person = personRegistry.verifyOrCreate(idName.trim(), "id_card", digest, null);
            PersonAccountLinker.expandAndLink(
                    expansionSource, userAccountService, person, "id_card", trimmedIdNo);
            binding.bind(resolved.provider(), resolved.subject(), person.id());

            UserAccount account = resolveAccount(person.id(), designatedUsername);
            username = account.username();
            String issueClient = clientId != null && !clientId.isBlank()
                    ? clientId.trim()
                    : bffProperties.getClientId();
            Map<String, Object> token = tokenIssuer.issueForAccount(issueClient, account);
            auditSink.login(request, LoginAttempt.of(true, username, issueClient, account.id(), LoginAudits.ID_NO, null));
            return token;
        } catch (RuntimeException ex) {
            String who = blankToNull(username) != null ? username : blankToNull(idName);
            String issueClient = clientId != null && !clientId.isBlank()
                    ? clientId.trim()
                    : bffProperties.getClientId();
            auditSink.login(request, LoginAttempt.of(false, who, issueClient, LoginAudits.ID_NO, ex.getMessage()));
            if (ex instanceof PlatformException) {
                log.warn("[pl] id-no reject username={} idName={} idNo={} -> {}",
                        dash(username), dash(idName), dash(idNo), ex.getMessage());
            } else {
                log.error("[pl] id-no fail username={} idName={} idNo={} -> {}",
                        dash(username), dash(idName), dash(idNo), ex.toString(), ex);
            }
            throw ex;
        }
    }

    private UserAccount resolveAccount(Long personId, String designatedUsername) {
        if (designatedUsername != null && !designatedUsername.isBlank()) {
            PrincipalDirectory directory = principalDirectory.getIfAvailable();
            if (directory == null) {
                throw new PlatformException("未启用多账号");
            }
            return directory.resolveDesignated(personId, designatedUsername.trim())
                    .orElseThrow(() -> new PlatformException("指定账号无效"));
        }
        List<UserAccount> accounts = userAccountService.findEnabledByPersonId(personId);
        if (accounts == null || accounts.isEmpty()) {
            throw new PlatformException("未找到可用账号");
        }
        return accounts.get(0);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
