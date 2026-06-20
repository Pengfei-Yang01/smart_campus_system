package com.example.campus.enums;

/**
 * 积分规则启用状态。
 */
public enum EffectiveStatus {
    /** 规则已启用，可以作为活动类型的默认规则。 */
    ENABLED,

    /** 规则仅保留历史记录，不应被自动选择。 */
    DISABLED
}
