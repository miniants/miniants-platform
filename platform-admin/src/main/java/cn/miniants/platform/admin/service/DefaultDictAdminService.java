package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.DictSave;
import cn.miniants.platform.admin.dto.DictTreeNode;
import cn.miniants.platform.admin.dto.DictVo;
import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.entity.Dict;
import cn.miniants.platform.admin.mapper.DictMapper;
import cn.miniants.platform.admin.support.EntityPages;
import cn.miniants.platform.admin.support.DictTrees;
import cn.miniants.platform.admin.support.PageQueries;
import cn.miniants.platform.admin.support.PersistIds;
import cn.miniants.platform.core.error.PlatformException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class DefaultDictAdminService implements DictAdminService {

    private final DictMapper dictMapper;

    public DefaultDictAdminService(DictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    @Override
    public PageResult<DictVo> page(Long current, Long size, String filter, String order, Long parentId) {
        var wrapper = EntityPages.wrapper(Dict.class, filter, order);
        if (parentId != null) {
            wrapper.eq("parent_id", parentId);
        }
        Page<Dict> page = dictMapper.selectPage(
                new Page<>(PageQueries.current(current), PageQueries.size(size)),
                wrapper);
        return PageResult.of(page, page.getRecords().stream().map(DefaultDictAdminService::toVo).toList());
    }

    @Override
    public DictVo get(Long id) {
        return toVo(requireDict(id));
    }

    @Override
    public List<DictTreeNode> tree(String dictType) {
        return DictTrees.build(dictMapper.selectList(Wrappers.<Dict>lambdaQuery()
                .eq(dictType != null && !dictType.isBlank(), Dict::getDictType,
                        dictType == null ? null : dictType.trim())));
    }

    @Override
    public DictVo check(DictSave body) {
        if (body == null) {
            throw new PlatformException("请求参数不正确");
        }
        Long parentId = normalizeParentId(body.getParentId());
        String dictCode = requireText(body.getDictCode(), "字典编码不能为空");
        String dictLabel = requireText(body.getDictLabel(), "字典标签不能为空");
        String dictType = resolveDictType(body.getDictType(), parentId, dictCode);
        validateParent(parentId, dictType, PersistIds.persisted(body.getId()) ? body.getId() : null);
        assertCodeAvailable(dictType, dictCode, body.getId());
        DictVo vo = new DictVo();
        vo.setId(body.getId());
        vo.setParentId(parentId);
        vo.setDictType(dictType);
        vo.setDictCode(dictCode);
        vo.setDictLabel(dictLabel);
        vo.setContent(blankToNull(body.getContent()));
        vo.setRemark(blankToNull(body.getRemark()));
        vo.setSortNo(body.getSortNo() == null ? 0 : body.getSortNo());
        vo.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        vo.setVersion(body.getVersion());
        return vo;
    }

    @Override
    @Transactional
    public DictVo save(DictSave body) {
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
        requireDict(id);
        Long children = dictMapper.selectCount(Wrappers.<Dict>lambdaQuery().eq(Dict::getParentId, id));
        if (children != null && children > 0) {
            throw new PlatformException("字典分类存在子类，请先删除子类");
        }
        dictMapper.deleteById(id);
    }

    private DictVo create(DictSave body) {
        Long parentId = normalizeParentId(body.getParentId());
        String dictCode = requireText(body.getDictCode(), "字典编码不能为空");
        Dict dict = new Dict();
        dict.setParentId(parentId);
        dict.setDictType(resolveDictType(body.getDictType(), parentId, dictCode));
        dict.setDictCode(dictCode);
        dict.setDictLabel(requireText(body.getDictLabel(), "字典标签不能为空"));
        dict.setContent(blankToNull(body.getContent()));
        dict.setRemark(blankToNull(body.getRemark()));
        dict.setSortNo(body.getSortNo() == null ? 0 : body.getSortNo());
        dict.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        validateParent(dict.getParentId(), dict.getDictType(), null);
        try {
            dictMapper.insert(dict);
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("字典项已存在");
        }
        return get(dict.getId());
    }

    private DictVo update(DictSave body) {
        Dict existing = requireDict(body.getId());
        if (body.getDictType() != null) {
            existing.setDictType(requireText(body.getDictType(), "字典类型不能为空"));
        }
        if (body.getParentId() != null) {
            Long parentId = normalizeParentId(body.getParentId());
            if (parentId != null && parentId.equals(existing.getId())) {
                throw new PlatformException("字典父节点不能是自身");
            }
            existing.setParentId(parentId);
        }
        if (body.getDictCode() != null) {
            existing.setDictCode(requireText(body.getDictCode(), "字典编码不能为空"));
        }
        if (body.getDictType() == null && (body.getDictCode() != null || body.getParentId() != null)) {
            existing.setDictType(resolveDictType(null, existing.getParentId(), existing.getDictCode()));
        }
        if (body.getDictLabel() != null) {
            existing.setDictLabel(requireText(body.getDictLabel(), "字典标签不能为空"));
        }
        if (body.getContent() != null) {
            existing.setContent(blankToNull(body.getContent()));
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
        validateParent(existing.getParentId(), existing.getDictType(), existing.getId());
        try {
            if (dictMapper.updateById(existing) == 0) {
                throw new PlatformException("字典不存在或已被修改");
            }
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("字典项已存在");
        }
        return get(existing.getId());
    }

    private Dict requireDict(Long id) {
        if (!PersistIds.persisted(id)) {
            throw new PlatformException("字典不存在");
        }
        Dict dict = dictMapper.selectById(id);
        if (dict == null) {
            throw new PlatformException("字典不存在");
        }
        return dict;
    }

    private void validateParent(Long parentId, String dictType, Long currentId) {
        Long cursor = parentId;
        while (cursor != null) {
            if (cursor.equals(currentId)) {
                throw new PlatformException("字典父子关系不能形成循环");
            }
            Dict parent = dictMapper.selectById(cursor);
            if (parent == null) {
                throw new PlatformException("字典父节点不存在");
            }
            if (!parent.getDictType().equals(dictType)) {
                throw new PlatformException("字典父节点类型不一致");
            }
            cursor = parent.getParentId();
        }
    }

    private static DictVo toVo(Dict dict) {
        DictVo vo = new DictVo();
        vo.setId(dict.getId());
        vo.setParentId(dict.getParentId());
        vo.setDictType(dict.getDictType());
        vo.setDictCode(dict.getDictCode());
        vo.setDictLabel(dict.getDictLabel());
        vo.setContent(dict.getContent());
        vo.setRemark(dict.getRemark());
        vo.setSortNo(dict.getSortNo());
        vo.setStatus(dict.getStatus());
        vo.setVersion(dict.getVersion());
        vo.setCreateTime(dict.getCreateTime());
        vo.setUpdateTime(dict.getUpdateTime());
        return vo;
    }

    private String resolveDictType(String dictType, Long parentId, String dictCode) {
        if (dictType != null && !dictType.isBlank()) {
            return dictType.trim();
        }
        if (parentId == null) {
            return requireText(dictCode, "字典编码不能为空");
        }
        Dict parent = requireDict(parentId);
        return parent.getDictType();
    }

    private void assertCodeAvailable(String dictType, String dictCode, Long currentId) {
        Long count = dictMapper.selectCount(Wrappers.<Dict>lambdaQuery()
                .eq(Dict::getDictType, dictType)
                .eq(Dict::getDictCode, dictCode)
                .ne(PersistIds.persisted(currentId), Dict::getId, currentId));
        if (count != null && count > 0) {
            throw new PlatformException("编码已存在，请勿重复添加");
        }
    }

    private static Long normalizeParentId(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return null;
        }
        return parentId;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
