package com.example.campus.enums;

/**
 * 学生事务申请的生命周期状态。
 */
public enum AffairApplicationStatus {
    /** 申请已提交，等待管理员审批。 */
    PENDING,

    /** 管理员已批准，申请可以按审批结果执行。 */
    APPROVED,

    /** 管理员已驳回，驳回原因会保存给申请人查看。 */
    REJECTED,

    /** 申请人在管理员审批前主动撤销。 */
    CANCELLED
}
