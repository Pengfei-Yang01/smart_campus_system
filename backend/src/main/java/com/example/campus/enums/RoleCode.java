package com.example.campus.enums;

import java.util.Collection;

/**
 * 认证和授权共同使用的系统角色编码。
 *
 * 数据库可以为同一用户保存多个角色，但前端
 * 需要一个主角色来决定展示哪个首页和菜单。
 * 优先级为管理员、组织负责人、学生。
 */
public enum RoleCode {
    /** 普通学生角色，用于浏览、报名和查看个人记录。 */
    STUDENT,

    /** 组织负责人角色，用于管理组织、活动和积分。 */
    ORG_LEADER,

    /** 系统管理员角色，拥有审核和维护权限。 */
    ADMIN;

    /**
     * 从用户角色集合中选择一个主角色。
     *
     * 用户在数据库中可能拥有多个角色，但前端需要
     * 单一角色来选择初始首页，因此这里选择权限最高的角色。
     *
     * @param roles 从角色关系表加载的角色编码
     * @return 当前会话使用的主角色
     */
    public static RoleCode primaryOf(Collection<String> roles) {
        if (roles != null && roles.contains(ADMIN.name())) {
            return ADMIN;
        }
        if (roles != null && roles.contains(ORG_LEADER.name())) {
            return ORG_LEADER;
        }
        return STUDENT;
    }
}
