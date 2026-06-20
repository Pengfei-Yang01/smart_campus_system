package com.example.campus.dto;

import com.example.campus.enums.AuditStatus;
import com.example.campus.enums.EffectiveStatus;

/**
 * 积分提交、审核和积分规则维护请求数据对象。
 */
public final class ScoreRequests {
    /**
     * 工具型封装类，不应被实例化。
     */
    private ScoreRequests() {
    }

    /**
     * 录入学生活动积分的请求体。
     *
     * @param activityId 已结束活动编号
     * @param userId 获得积分的学生用户编号
     * @param baseScore 可选手动基础分；为空时使用活动或规则基础分
     */
    public record ScoreRecordRequest(Long activityId, Long userId, Double baseScore) {
    }

    /**
     * 管理员审核积分的请求体。
     *
 * @param auditStatus 审核结果；控制器只接受通过或驳回
     * @param rejectReason 驳回积分时的可选原因
     */
    public record ScoreAuditRequest(AuditStatus auditStatus, String rejectReason) {
    }

    /**
     * 创建或编辑积分规则的请求体。
     *
     * @param typeId 该规则适用的活动类型
     * @param baseScore 该类型默认基础分
     * @param normalWeight 普通参与者权重
     * @param memberWeight 组织成员权重
     * @param leaderWeight 组织负责人权重
     * @param ruleDesc 可读的规则说明
     * @param effectiveStatus 当前规则是否启用
     */
    public record ScoreRuleRequest(
            Long typeId,
            Double baseScore,
            Double normalWeight,
            Double memberWeight,
            Double leaderWeight,
            String ruleDesc,
            EffectiveStatus effectiveStatus
    ) {
    }
}
