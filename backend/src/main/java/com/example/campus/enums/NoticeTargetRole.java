package com.example.campus.enums;

/**
 * 公告面向的主角色。
 */
public enum NoticeTargetRole {
    /** 面向全部已启用用户。 */
    ALL,

    /** 只面向主角色为学生的用户。 */
    STUDENT,

    /** 只面向主角色为组织负责人的用户。 */
    ORG_LEADER,

    /** 只面向系统管理员。 */
    ADMIN
}
