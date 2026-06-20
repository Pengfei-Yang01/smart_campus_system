package com.example.campus.enums;

/**
 * 用户账号可用状态，对应用户账号表中的状态字段。
 */
public enum AccountStatus {
    /** 账号可登录并访问受保护接口。 */
    ENABLED,

    /** 账号已被管理员禁用，不能登录。 */
    DISABLED
}
