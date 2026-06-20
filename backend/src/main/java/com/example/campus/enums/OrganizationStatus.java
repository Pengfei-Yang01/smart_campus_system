package com.example.campus.enums;

/**
 * 组织生命周期状态。停用组织会保留历史数据，
 * 但不能再用于发布新活动。
 */
public enum OrganizationStatus {
    /** 组织处于申请中或类似草稿的待处理状态。 */
    PENDING,

    /** 组织已启用，可以举办活动。 */
    ACTIVE,

    /** 组织已停用，但历史数据仍可查询。 */
    DISABLED
}
