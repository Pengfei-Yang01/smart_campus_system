package com.example.campus.enums;

/**
 * 活动有限状态集合，名称与数据库枚举值一致。
 */
public enum ActivityStatus {
    /** 活动已保存，但还未开放报名。 */
    DRAFT,

    /** 活动对学生可见，并接受报名。 */
    OPEN,

    /** 报名已经停止，但活动尚未最终结束。 */
    CLOSED,

    /** 活动已经结束，可以用于录入积分。 */
    FINISHED,

    /** 活动已下架，不应再接受后续业务操作。 */
    OFFLINE
}
