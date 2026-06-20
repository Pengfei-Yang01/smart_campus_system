package com.example.campus.security;

import java.util.Set;

/**
 * 存放在请求线程上下文中的认证用户快照。
 *
 * @param userId 用户账号表主键
 * @param username 登录用户名
 * @param realName 界面展示名称
 * @param roles 当前令牌携带的角色编码
 */
public record CurrentUser(Long userId, String username, String realName, Set<String> roles) {
    /**
     * 检查令牌中是否包含指定角色编码。
     *
     * @param role 角色编码
     * @return 当前令牌拥有该角色时返回真
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * @return 当前用户以管理员身份操作时返回真
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * @return 当前用户以组织负责人身份操作时返回真
     */
    public boolean isLeader() {
        return hasRole("ORG_LEADER");
    }
}
