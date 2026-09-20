package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.OauthClientSave;
import cn.miniants.platform.admin.dto.OauthClientVo;
import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.entity.OauthClient;
import cn.miniants.platform.admin.mapper.OauthClientMapper;
import cn.miniants.platform.admin.oauth.OauthClientChangeListener;
import cn.miniants.platform.admin.support.EntityPages;
import cn.miniants.platform.admin.support.PageQueries;
import cn.miniants.platform.admin.support.PersistIds;
import cn.miniants.platform.admin.support.SecretGenerator;
import cn.miniants.platform.core.error.PlatformException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class DefaultOauthClientAdminService implements OauthClientAdminService {

    private static final String DEFAULT_GRANTS = "client_credentials";
    private static final String DEFAULT_SCOPES = "default";

    private final OauthClientMapper oauthClientMapper;
    private final PasswordEncoder passwordEncoder;
    private final List<OauthClientChangeListener> changeListeners;

    public DefaultOauthClientAdminService(OauthClientMapper oauthClientMapper, PasswordEncoder passwordEncoder) {
        this(oauthClientMapper, passwordEncoder, List.of());
    }

    public DefaultOauthClientAdminService(OauthClientMapper oauthClientMapper, PasswordEncoder passwordEncoder,
            List<OauthClientChangeListener> changeListeners) {
        this.oauthClientMapper = oauthClientMapper;
        this.passwordEncoder = passwordEncoder;
        this.changeListeners = changeListeners == null ? List.of() : List.copyOf(changeListeners);
    }

    @Override
    public PageResult<OauthClientVo> page(Long current, Long size, String filter, String order) {
        Page<OauthClient> page = oauthClientMapper.selectPage(
                new Page<>(PageQueries.current(current), PageQueries.size(size)),
                EntityPages.wrapper(OauthClient.class, filter, order, "clientSecret"));
        return PageResult.of(page, page.getRecords().stream()
                .map(client -> toVo(client, null))
                .toList());
    }

    @Override
    public OauthClientVo get(Long id) {
        return toVo(requireClient(id), null);
    }

    @Override
    @Transactional
    public OauthClientVo save(OauthClientSave body) {
        if (body == null) {
            throw new PlatformException("请求参数不正确");
        }
        if (PersistIds.persisted(body.getId())) {
            return update(body);
        }
        return create(body);
    }

    @Override
    @Transactional
    public OauthClientVo resetSecret(Long id) {
        OauthClient existing = requireClient(id);
        String plaintext = SecretGenerator.plaintext();
        existing.setClientSecret(passwordEncoder.encode(plaintext));
        if (oauthClientMapper.updateById(existing) == 0) {
            throw new PlatformException("客户端不存在或已被修改");
        }
        notifyChanged(existing.getClientId());
        return toVo(requireClient(id), plaintext);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        OauthClient existing = requireClient(id);
        oauthClientMapper.deleteById(id);
        notifyChanged(existing.getClientId());
    }

    private OauthClientVo create(OauthClientSave body) {
        String plaintext = SecretGenerator.plaintext();
        OauthClient client = new OauthClient();
        client.setClientId(requireText(body.getClientId(), "客户端标识不能为空"));
        client.setClientSecret(passwordEncoder.encode(plaintext));
        client.setClientName(blankToNull(body.getClientName()));
        client.setGrantTypes(firstNonBlank(body.getGrantTypes(), DEFAULT_GRANTS));
        client.setScopes(firstNonBlank(body.getScopes(), DEFAULT_SCOPES));
        client.setAccessTokenTtl(body.getAccessTokenTtl() == null ? 3600 : body.getAccessTokenTtl());
        client.setRefreshTokenTtl(body.getRefreshTokenTtl() == null ? 86400 : body.getRefreshTokenTtl());
        client.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        client.setRemark(blankToNull(body.getRemark()));
        try {
            oauthClientMapper.insert(client);
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("客户端标识已存在");
        }
        notifyChanged(client.getClientId());
        return toVo(requireClient(client.getId()), plaintext);
    }

    private OauthClientVo update(OauthClientSave body) {
        OauthClient existing = requireClient(body.getId());
        String previousClientId = existing.getClientId();
        if (body.getClientId() != null) {
            existing.setClientId(requireText(body.getClientId(), "客户端标识不能为空"));
        }
        if (body.getClientName() != null) {
            existing.setClientName(blankToNull(body.getClientName()));
        }
        if (body.getGrantTypes() != null) {
            existing.setGrantTypes(requireText(body.getGrantTypes(), "授权类型不能为空"));
        }
        if (body.getScopes() != null) {
            existing.setScopes(requireText(body.getScopes(), "授权范围不能为空"));
        }
        if (body.getAccessTokenTtl() != null) {
            existing.setAccessTokenTtl(body.getAccessTokenTtl());
        }
        if (body.getRefreshTokenTtl() != null) {
            existing.setRefreshTokenTtl(body.getRefreshTokenTtl());
        }
        if (body.getStatus() != null) {
            existing.setStatus(body.getStatus());
        }
        if (body.getRemark() != null) {
            existing.setRemark(blankToNull(body.getRemark()));
        }
        if (body.getVersion() != null) {
            existing.setVersion(body.getVersion());
        }
        try {
            if (oauthClientMapper.updateById(existing) == 0) {
                throw new PlatformException("客户端不存在或已被修改");
            }
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("客户端标识已存在");
        }
        if (!previousClientId.equals(existing.getClientId())) {
            notifyChanged(previousClientId);
        }
        notifyChanged(existing.getClientId());
        return get(existing.getId());
    }

    private void notifyChanged(String clientId) {
        for (OauthClientChangeListener listener : changeListeners) {
            listener.changed(clientId);
        }
    }

    private OauthClient requireClient(Long id) {
        if (!PersistIds.persisted(id)) {
            throw new PlatformException("客户端不存在");
        }
        OauthClient client = oauthClientMapper.selectById(id);
        if (client == null) {
            throw new PlatformException("客户端不存在");
        }
        return client;
    }

    private static OauthClientVo toVo(OauthClient client, String plaintextSecret) {
        OauthClientVo vo = new OauthClientVo();
        vo.setId(client.getId());
        vo.setClientId(client.getClientId());
        vo.setClientName(client.getClientName());
        vo.setGrantTypes(client.getGrantTypes());
        vo.setScopes(client.getScopes());
        vo.setAccessTokenTtl(client.getAccessTokenTtl());
        vo.setRefreshTokenTtl(client.getRefreshTokenTtl());
        vo.setStatus(client.getStatus());
        vo.setRemark(client.getRemark());
        vo.setSecretConfigured(client.getClientSecret() != null && !client.getClientSecret().isBlank());
        vo.setVersion(client.getVersion());
        vo.setCreateTime(client.getCreateTime());
        vo.setUpdateTime(client.getUpdateTime());
        vo.setPlaintextSecret(plaintextSecret);
        return vo;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
