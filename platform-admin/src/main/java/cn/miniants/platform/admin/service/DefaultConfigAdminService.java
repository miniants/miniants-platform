package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.config.ConfigSource;
import cn.miniants.platform.admin.dto.ConfigSave;
import cn.miniants.platform.admin.dto.ConfigVo;
import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.entity.Config;
import cn.miniants.platform.admin.mapper.ConfigMapper;
import cn.miniants.platform.admin.support.EntityPages;
import cn.miniants.platform.admin.support.PageQueries;
import cn.miniants.platform.admin.support.PersistIds;
import cn.miniants.platform.core.error.PlatformException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

public class DefaultConfigAdminService implements ConfigAdminService {

    private final ConfigMapper configMapper;
    private final ConfigSource configSource;

    public DefaultConfigAdminService(ConfigMapper configMapper, ConfigSource configSource) {
        this.configMapper = configMapper;
        this.configSource = configSource;
    }

    @Override
    public PageResult<ConfigVo> page(Long current, Long size, String filter, String order) {
        Page<Config> page = configMapper.selectPage(
                new Page<>(PageQueries.current(current), PageQueries.size(size)),
                EntityPages.wrapper(Config.class, filter, order));
        return PageResult.of(page, page.getRecords().stream().map(DefaultConfigAdminService::toVo).toList());
    }

    @Override
    public ConfigVo get(Long id) {
        return toVo(requireConfig(id));
    }

    @Override
    @Transactional
    public ConfigVo save(ConfigSave body) {
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
    public void delete(Long id) {
        Config existing = requireConfig(id);
        configMapper.deleteById(id);
        evict(existing.getCategory(), existing.getConfigKey());
    }

    private void evict(String category, String key) {
        if (configSource == null || category == null || category.isBlank() || key == null || key.isBlank()) {
            return;
        }
        configSource.evict(category, key);
    }

    private ConfigVo create(ConfigSave body) {
        Config config = new Config();
        config.setCategory(blankToNull(body.getCategory()));
        config.setConfigKey(requireText(body.getConfigKey(), "配置键不能为空"));
        config.setTitle(blankToNull(body.getTitle()));
        config.setConfigValue(body.getConfigValue());
        config.setRemark(blankToNull(body.getRemark()));
        config.setSortNo(body.getSortNo() == null ? 0 : body.getSortNo());
        config.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        try {
            configMapper.insert(config);
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("配置键已存在");
        }
        evict(config.getCategory(), config.getConfigKey());
        return get(config.getId());
    }

    private ConfigVo update(ConfigSave body) {
        Config existing = requireConfig(body.getId());
        String previousCategory = existing.getCategory();
        String previousKey = existing.getConfigKey();
        if (body.getCategory() != null) {
            existing.setCategory(blankToNull(body.getCategory()));
        }
        if (body.getConfigKey() != null) {
            existing.setConfigKey(requireText(body.getConfigKey(), "配置键不能为空"));
        }
        if (body.getTitle() != null) {
            existing.setTitle(blankToNull(body.getTitle()));
        }
        if (body.getConfigValue() != null) {
            existing.setConfigValue(body.getConfigValue());
        }
        if (body.getRemark() != null) {
            existing.setRemark(blankToNull(body.getRemark()));
        }
        if (body.getSortNo() != null) {
            existing.setSortNo(body.getSortNo());
        }
        if (body.getStatus() != null) {
            existing.setStatus(body.getStatus());
        }
        if (body.getVersion() != null) {
            existing.setVersion(body.getVersion());
        }
        try {
            if (configMapper.updateById(existing) == 0) {
                throw new PlatformException("配置不存在或已被修改");
            }
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("配置键已存在");
        }
        evict(previousCategory, previousKey);
        evict(existing.getCategory(), existing.getConfigKey());
        return get(existing.getId());
    }

    private Config requireConfig(Long id) {
        if (!PersistIds.persisted(id)) {
            throw new PlatformException("配置不存在");
        }
        Config config = configMapper.selectById(id);
        if (config == null) {
            throw new PlatformException("配置不存在");
        }
        return config;
    }

    private static ConfigVo toVo(Config config) {
        ConfigVo vo = new ConfigVo();
        vo.setId(config.getId());
        vo.setCategory(config.getCategory());
        vo.setConfigKey(config.getConfigKey());
        vo.setTitle(config.getTitle());
        vo.setConfigValue(config.getConfigValue());
        vo.setRemark(config.getRemark());
        vo.setSortNo(config.getSortNo());
        vo.setStatus(config.getStatus());
        vo.setVersion(config.getVersion());
        vo.setCreateTime(config.getCreateTime());
        vo.setUpdateTime(config.getUpdateTime());
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
}
