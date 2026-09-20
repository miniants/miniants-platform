package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.OperLogRecord;
import cn.miniants.platform.admin.dto.OperLogVo;
import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.entity.OperLog;
import cn.miniants.platform.admin.mapper.OperLogMapper;
import cn.miniants.platform.admin.support.EntityPages;
import cn.miniants.platform.admin.support.OperLogCollapse;
import cn.miniants.platform.admin.support.PageQueries;
import cn.miniants.platform.admin.support.PersistIds;
import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.CurrentUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public class DefaultOperLogAdminService implements OperLogAdminService {

    private static final int MAX_PARAM = 2000;

    private final OperLogMapper operLogMapper;

    public DefaultOperLogAdminService(OperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    @Override
    public PageResult<OperLogVo> page(Long current, Long size, String filter, String order, boolean collapse) {
        long cur = PageQueries.current(current);
        long pageSize = PageQueries.size(size);
        if (!collapse) {
            Page<OperLog> page = operLogMapper.selectPage(
                    new Page<>(cur, pageSize),
                    EntityPages.wrapper(OperLog.class, filter, order));
            return PageResult.of(page, page.getRecords().stream().map(DefaultOperLogAdminService::toVo).toList());
        }
        List<OperLog> collapsed = OperLogCollapse.mergeConsecutive(
                operLogMapper.selectList(EntityPages.wrapper(OperLog.class, filter, "{\"createTime\":\"DESC\"}")));
        long total = collapsed.size();
        int from = (int) Math.min((cur - 1L) * pageSize, total);
        int to = (int) Math.min(from + pageSize, total);
        List<OperLogVo> records = from >= total
                ? List.of()
                : collapsed.subList(from, to).stream().map(DefaultOperLogAdminService::toVo).toList();
        return PageResult.of(records, total, cur, pageSize);
    }

    @Override
    public OperLogVo get(Long id) {
        return toVo(requireLog(id));
    }

    @Override
    @Transactional
    public OperLogVo record(OperLogRecord body) {
        if (body == null || body.getTitle() == null || body.getTitle().isBlank()) {
            throw new PlatformException("操作标题不能为空");
        }
        OperLog log = new OperLog();
        log.setTitle(body.getTitle().trim());
        log.setEventType(blankToNull(body.getEventType()));
        log.setTraceId(serverTraceId());
        log.setGroupCode(blankToNull(body.getGroupCode()));
        log.setRepeatCount(body.getRepeatCount() == null ? 1 : body.getRepeatCount());
        log.setHttpMethod(blankToNull(body.getHttpMethod()));
        log.setHttpStatus(body.getHttpStatus());
        log.setRequestUri(blankToNull(body.getRequestUri()));
        log.setRequestParam(truncate(body.getRequestParam()));
        log.setSuccess(body.getSuccess() == null ? 1 : body.getSuccess());
        log.setErrorMessage(blankToNull(body.getErrorMessage()));
        log.setOperatorId(serverOperatorId());
        log.setOperatorName(serverOperatorName(body.getOperatorName()));
        log.setRequestIp(blankToNull(body.getRequestIp()));
        log.setCostMs(body.getCostMs());
        log.setCreateTime(LocalDateTime.now());
        operLogMapper.insert(log);
        return get(log.getId());
    }

    private static Long serverOperatorId() {
        CurrentUser user = CurrentUser.find();
        return user == null ? null : user.userId();
    }

    private static String serverOperatorName(String fallback) {
        CurrentUser user = CurrentUser.find();
        if (user != null && user.username() != null && !user.username().isBlank()) {
            return user.username().trim();
        }
        return blankToNull(fallback);
    }

    private static String serverTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? null : traceId.trim();
    }

    private OperLog requireLog(Long id) {
        if (!PersistIds.persisted(id)) {
            throw new PlatformException("操作日志不存在");
        }
        OperLog log = operLogMapper.selectById(id);
        if (log == null) {
            throw new PlatformException("操作日志不存在");
        }
        return log;
    }

    private static OperLogVo toVo(OperLog log) {
        OperLogVo vo = new OperLogVo();
        vo.setId(log.getId());
        vo.setTitle(log.getTitle());
        vo.setEventType(log.getEventType());
        vo.setTraceId(log.getTraceId());
        vo.setGroupCode(log.getGroupCode());
        vo.setRepeatCount(log.getRepeatCount());
        vo.setHttpMethod(log.getHttpMethod());
        vo.setHttpStatus(log.getHttpStatus());
        vo.setRequestUri(log.getRequestUri());
        vo.setRequestParam(log.getRequestParam());
        vo.setSuccess(log.getSuccess());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(log.getOperatorName());
        vo.setRequestIp(log.getRequestIp());
        vo.setCostMs(log.getCostMs());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_PARAM ? trimmed : trimmed.substring(0, MAX_PARAM);
    }
}
