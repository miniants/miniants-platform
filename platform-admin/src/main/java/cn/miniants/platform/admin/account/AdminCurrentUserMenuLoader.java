package cn.miniants.platform.admin.account;

import cn.miniants.platform.admin.service.ResourceAdminService;
import cn.miniants.platform.security.CurrentUser;
import cn.miniants.platform.security.CurrentUserMenuLoader;

public class AdminCurrentUserMenuLoader implements CurrentUserMenuLoader {

    private final ResourceAdminService resourceAdminService;

    public AdminCurrentUserMenuLoader(ResourceAdminService resourceAdminService) {
        this.resourceAdminService = resourceAdminService;
    }

    @Override
    public Object load(CurrentUser user) {
        return resourceAdminService.mine();
    }
}