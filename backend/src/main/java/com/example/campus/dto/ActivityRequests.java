package com.example.campus.dto;

import com.example.campus.enums.ActivityStatus;
import com.example.campus.enums.CheckinStatus;

/**
 * 活动发布、状态流转和签到请求数据对象。
 *
 * 这些记录类描述活动相关接口接收的请求体，
 * 把请求结构集中在这里，可以避免控制器使用松散的键值表，
 * 也让后端开发者和前端调用方都能清楚看到字段名。
 */
public final class ActivityRequests {
    /**
     * 工具型封装类，不应被实例化。
     */
    private ActivityRequests() {
    }

    /**
     * 活动创建和更新请求。
     *
     * @param activityName 活动中心展示的活动标题
     * @param typeId 活动类型编号，对应 activity_type.type_id
     * @param orgId 主办组织编号
     * @param startTime 活动开始时间，格式为常用日期时间文本或标准本地时间
     * @param endTime 活动结束时间，不能早于 startTime
     * @param registrationDeadline 报名截止时间，不能晚于 startTime
     * @param location 线下地点或线上地址文本
     * @param capacity 有效报名人数上限
     * @param description 详情页展示的公开简介
     * @param requirement 参与说明或前置要求
     * @param baseScore 当前活动可选的基础分覆盖值
     * @param ruleId 可选积分规则编号；为空时使用 typeId 对应的启用规则
     * @param activityStatus 创建活动时使用的初始状态
     */
    public record ActivityRequest(
            String activityName,
            Long typeId,
            Long orgId,
            String startTime,
            String endTime,
            String registrationDeadline,
            String location,
            Integer capacity,
            String description,
            String requirement,
            Double baseScore,
            Long ruleId,
            ActivityStatus activityStatus
    ) {
    }

    /**
     * 修改活动生命周期状态的请求体。
     *
     * @param status 目标活动状态
     */
    public record ActivityStatusRequest(ActivityStatus status) {
    }

    /**
     * 标记报名签到结果的请求体。
     *
     * @param checkinStatus 签到状态，表示未签到、已签到或缺勤
     */
    public record CheckinRequest(CheckinStatus checkinStatus) {
    }
}
