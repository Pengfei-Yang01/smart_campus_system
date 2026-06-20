package com.example.campus.enums;

/**
 * 负责人申请、组织申请和积分记录共用的审核状态。
 * 
 */
public enum AuditStatus {
    /** 等待管理员审核。 */
    PENDING,

    /** 审核通过，应执行对应业务效果。 */
    APPROVED,

    /** 审核不通过，可通过驳回原因说明。 */
    REJECTED
}
