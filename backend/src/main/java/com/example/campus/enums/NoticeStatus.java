package com.example.campus.enums;

/**
 * 管理员公告的发布状态。
 */
public enum NoticeStatus {
    /** 已发布，并已向当前匹配用户生成站内消息。 */
    PUBLISHED,

    /** 已停用，不再作为有效公告展示。 */
    DISABLED
}
