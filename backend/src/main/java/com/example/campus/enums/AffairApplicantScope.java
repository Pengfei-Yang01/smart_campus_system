package com.example.campus.enums;

/**
 * 事务申请类型允许的最低申请角色范围。
 */
public enum AffairApplicantScope {
    /** 普通学生、组织负责人都可以提交。 */
    ALL,

    /** 只有组织负责人可以提交，通常用于教室、场地等组织活动资源。 */
    ORG_LEADER
}
