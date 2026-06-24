package com.example.campus.dto;

import com.example.campus.enums.FeedbackStatus;

/**
 * 活动评价反馈模块的请求数据对象。
 */
public final class FeedbackRequests {
    /**
     * 工具型封装类，不应被实例化。
     */
    private FeedbackRequests() {
    }

    /**
     * 学生提交活动评价的请求体。
     *
     * @param rating 1 到 5 星评分
     * @param content 文字反馈内容
     * @param anonymous 是否匿名展示
     */
    public record FeedbackRequest(Integer rating, String content, Boolean anonymous) {
    }

    /**
     * 组织负责人或管理员回复评价的请求体。
     *
     * @param replyContent 回复内容
     */
    public record FeedbackReplyRequest(String replyContent) {
    }

    /**
     * 调整评价可见状态的请求体。
     *
     * @param status 评价展示状态
     */
    public record FeedbackStatusRequest(FeedbackStatus status) {
    }
}
