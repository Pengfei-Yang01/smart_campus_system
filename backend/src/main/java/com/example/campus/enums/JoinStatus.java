package com.example.campus.enums;

/**
 * 学生加入组织申请的状态。
 */
public enum JoinStatus {
    /** 学生已提交申请，等待审核。 */
    PENDING,

    /** 学生是当前有效组织成员。 */
    APPROVED,

    /** 学生申请已被驳回。 */
    REJECTED,

    /** 学生已退出或不再是组织有效成员。 */
    QUIT
}
