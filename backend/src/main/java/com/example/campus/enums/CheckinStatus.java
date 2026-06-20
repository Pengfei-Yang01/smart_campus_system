package com.example.campus.enums;

/**
 * 活动报名记录的签到状态。
 */
public enum CheckinStatus {
    /** 已有报名记录，但尚未判定出勤情况。 */
    NOT_CHECKED,

    /** 学生已参加并成功签到。 */
    CHECKED,

    /** 学生未参加或签到验证未通过。 */
    ABSENT
}
