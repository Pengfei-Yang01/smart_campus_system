package com.example.campus.enums;

/**
 * 消息中心用于区分消息来源和展示标签的类型。
 */
public enum MessageCategory {
    /** 系统生成的普通提醒。 */
    SYSTEM,

    /** 管理员发布的公告通知。 */
    NOTICE,

    /** 审批结果类消息。 */
    AUDIT,

    /** 学生事务申请相关消息。 */
    AFFAIR,

    /** 活动评价和回复相关消息。 */
    FEEDBACK
}
