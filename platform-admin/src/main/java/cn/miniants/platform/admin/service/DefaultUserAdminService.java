package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.UserProfileDto;
import cn.miniants.platform.admin.dto.UserSave;
import cn.miniants.platform.admin.dto.UserVo;
import cn.miniants.platform.admin.entity.User;
import cn.miniants.platform.admin.entity.UserProfile;
import cn.miniants.platform.admin.entity.UserRole;
import cn.miniants.platform.admin.mapper.UserMapper;
import cn.miniants.platform.admin.mapper.UserProfileMapper;
import cn.miniants.platform.admin.mapper.UserRoleMapper;
import cn.miniants.platform.admin.support.AdminReferenceValidator;
import cn.miniants.platform.admin.support.PageQueries;
import cn.miniants.platform.admin.support.PersistIds;
import cn.miniants.platform.admin.mapper.RoleMapper;
import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.data.query.EntityQuery;
import cn.miniants.platform.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

public class DefaultUserAdminService implements UserAdminService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleMapper roleMapper;

    public DefaultUserAdminService(UserMapper userMapper, UserRoleMapper userRoleMapper,
            UserProfileMapper userProfileMapper, PasswordEncoder passwordEncoder, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
        this.roleMapper = roleMapper;
    }

    @Override
    public PageResult<UserVo> page(Long current, Long size, String filter, String order, Long roleId) {
        @SuppressWarnings("unchecked")
        QueryWrapper<User> wrapper = (QueryWrapper<User>) EntityQuery.of(User.class)
                .exclude("password")
                .tableAlias("u")
                .allow("p", UserProfile.class)
                .wrapper(filter, order);
        wrapper.apply(roleId != null,
                "EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = {0})",
                roleId);
        Page<User> page = new Page<>(PageQueries.current(current), PageQueries.size(size));
        userMapper.selectAdminPage(page, wrapper);
        List<Long> ids = page.getRecords().stream().map(User::getId).toList();
        Map<Long, List<Long>> roles = roleIdsByUser(ids);
        Map<Long, UserProfile> profiles = profilesByUser(ids);
        List<UserVo> records = page.getRecords().stream()
                .map(user -> toVo(user, roles.getOrDefault(user.getId(), List.of()), profiles.get(user.getId())))
                .toList();
        return PageResult.of(page, records);
    }

    @Override
    public UserVo get(Long id) {
        User user = requireUser(id);
        return toVo(user, roleIdsByUser(List.of(id)).getOrDefault(id, List.of()),
                userProfileMapper.selectById(id));
    }

    @Override
    @Transactional
    public UserVo save(UserSave body) {
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
        User existing = requireUser(id);
        if (Integer.valueOf(1).equals(existing.getSysAdmin())) {
            requireSysAdmin();
        }
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, id));
        userProfileMapper.deleteById(id);
        userMapper.deleteById(id);
    }

    private UserVo create(UserSave body) {
        String username = requireText(body.getUsername(), "用户名不能为空");
        String password = requireText(body.getPassword(), "新建用户必须设置密码");
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setDisplayName(blankToNull(body.getDisplayName()));
        user.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        if (Integer.valueOf(1).equals(body.getSysAdmin())) {
            requireSysAdmin();
        }
        user.setSysAdmin(body.getSysAdmin() == null ? 0 : body.getSysAdmin());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("用户名已存在");
        }
        replaceRoles(user.getId(), body.getRoleIds());
        saveProfile(user.getId(), body.getProfile());
        return get(user.getId());
    }

    private UserVo update(UserSave body) {
        User existing = requireUser(body.getId());
        if (body.getUsername() != null) {
            existing.setUsername(requireText(body.getUsername(), "用户名不能为空"));
        }
        if (body.getPassword() != null && !body.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(body.getPassword()));
        }
        if (body.getDisplayName() != null) {
            existing.setDisplayName(blankToNull(body.getDisplayName()));
        }
        if (body.getStatus() != null) {
            existing.setStatus(body.getStatus());
        }
        if (body.getSysAdmin() != null) {
            if (!Objects.equals(existing.getSysAdmin(), body.getSysAdmin())) {
                requireSysAdmin();
            }
            existing.setSysAdmin(body.getSysAdmin());
        }
        if (body.getVersion() != null) {
            existing.setVersion(body.getVersion());
        }
        try {
            if (userMapper.updateById(existing) == 0) {
                throw new PlatformException("用户不存在或已被修改");
            }
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("用户名已存在");
        }
        if (body.getRoleIds() != null) {
            replaceRoles(existing.getId(), body.getRoleIds());
        }
        saveProfile(existing.getId(), body.getProfile());
        return get(existing.getId());
    }

    /**
     * 不传就不动已有资料；传了整体覆盖。
     *
     * <p>手机 / 邮箱一旦改了就把对应的 verified 清零：留着旧标记等于把一个没验证过的号码
     * 当成已验证，后续找回口令之类的流程会直接发到那里。
     */
    private void saveProfile(Long userId, UserProfileDto profile) {
        if (profile == null) {
            return;
        }
        UserProfile existing = userProfileMapper.selectById(userId);
        UserProfile row = new UserProfile();
        row.setUserId(userId);
        row.setRealName(profile.getRealName());
        row.setNickName(profile.getNickName());
        row.setAvatar(profile.getAvatar());
        row.setSex(profile.getSex());
        row.setPhone(profile.getPhone());
        row.setEmail(profile.getEmail());
        row.setPhoneVerified(keptVerified(existing == null ? null : existing.getPhone(), profile.getPhone(),
                existing == null ? null : existing.getPhoneVerified()));
        row.setEmailVerified(keptVerified(existing == null ? null : existing.getEmail(), profile.getEmail(),
                existing == null ? null : existing.getEmailVerified()));
        if (existing == null) {
            userProfileMapper.insert(row);
        } else {
            userProfileMapper.updateById(row);
        }
    }

    private static Integer keptVerified(String oldValue, String newValue, Integer oldVerified) {
        if (oldValue == null || !oldValue.equals(newValue)) {
            return 0;
        }
        return oldVerified == null ? 0 : oldVerified;
    }

    private Map<Long, UserProfile> profilesByUser(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserProfile> byUser = new LinkedHashMap<>();
        for (UserProfile row : userProfileMapper.selectByIds(userIds)) {
            byUser.put(row.getUserId(), row);
        }
        return byUser;
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        AdminReferenceValidator.requireRoles(roleMapper, roleIds);
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<Long> unique = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        for (Long roleId : unique) {
            UserRole link = new UserRole();
            link.setUserId(userId);
            link.setRoleId(roleId);
            link.setCreateTime(LocalDateTime.now());
            userRoleMapper.insert(link);
        }
    }

    private Map<Long, List<Long>> roleIdsByUser(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserRole> links = userRoleMapper.selectList(
                Wrappers.<UserRole>lambdaQuery().in(UserRole::getUserId, userIds));
        Map<Long, List<Long>> grouped = new LinkedHashMap<>();
        for (UserRole link : links) {
            grouped.computeIfAbsent(link.getUserId(), key -> new ArrayList<>()).add(link.getRoleId());
        }
        return grouped;
    }

    private User requireUser(Long id) {
        if (!PersistIds.persisted(id)) {
            throw new PlatformException("用户不存在");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new PlatformException("用户不存在");
        }
        return user;
    }

    private static UserVo toVo(User user, List<Long> roleIds, UserProfile profile) {
        UserVo vo = new UserVo();
        if (profile != null) {
            UserProfileDto dto = new UserProfileDto();
            dto.setRealName(profile.getRealName());
            dto.setNickName(profile.getNickName());
            dto.setAvatar(profile.getAvatar());
            dto.setSex(profile.getSex());
            dto.setPhone(profile.getPhone());
            dto.setEmail(profile.getEmail());
            vo.setProfile(dto);
        }
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setStatus(user.getStatus());
        vo.setSysAdmin(user.getSysAdmin());
        vo.setRoleIds(roleIds == null ? List.of() : List.copyOf(roleIds));
        vo.setVersion(user.getVersion());
        vo.setCreateBy(user.getCreateBy());
        vo.setUpdateBy(user.getUpdateBy());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(message);
        }
        return value.trim();
    }

    private static void requireSysAdmin() {
        CurrentUser current = CurrentUser.find();
        if (current == null || !current.sysAdmin()) {
            throw new PlatformException("仅系统管理员可修改超管属性");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
