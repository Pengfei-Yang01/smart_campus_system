package com.example.campus.dto;

import com.example.campus.enums.NoticePriority;
import com.example.campus.enums.NoticeTargetRole;

/**
 * 消息通知中心的请求数据对象。
 */
public final class MessageRequests {
    /**
     * 工具型封装类，不应被实例化。
     */
    private MessageRequests() {
    }

    /**
     * 管理员发布公告的请求体。
     *
     * @param title 公告标题
     * @param content 公告内容
     * @param targetRole 接收公告的目标主角色
     * @param priority 公告优先级
     */
    public record NoticeRequest(
            String title,
            String content,
            NoticeTargetRole targetRole,
            NoticePriority priority
    ) {
    }
}
