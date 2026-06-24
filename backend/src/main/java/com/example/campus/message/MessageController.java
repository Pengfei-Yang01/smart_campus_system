package com.example.campus.message;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.MessageRequests.NoticeRequest;
import com.example.campus.enums.NoticePriority;
import com.example.campus.enums.NoticeTargetRole;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.UserContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息通知中心接口。
 *
 * 普通用户读取自己的收件箱并标记已读；
 * 管理员还可以发布面向指定角色的公告。
 */
@RestController
@RequestMapping("/api")
public class MessageController {
    private final Db db;
    private final MessageService messageService;

    /**
     * 注入数据库工具和消息写入服务。
     */
    public MessageController(Db db, MessageService messageService) {
        this.db = db;
        this.messageService = messageService;
    }

    /**
     * 查询当前用户的站内消息。
     *
     * @param unread 为 true 时只返回未读消息
     */
    @GetMapping("/messages")
    public ApiResponse<Object> messages(@RequestParam(required = false) Boolean unread) {
        CurrentUser user = UserContext.get();
        String unreadCondition = Boolean.TRUE.equals(unread) ? " and m.read_at is null\n" : "";
        return ApiResponse.ok(db.jdbc().queryForList("""
                select m.*, n.target_role, n.priority, n.notice_status
                from user_message m
                left join notice n on m.source_notice_id = n.notice_id
                where m.recipient_id = ?
                """ + unreadCondition + """
                order by m.created_at desc, m.message_id desc
                """, user.userId()));
    }

    /**
     * 返回当前用户未读消息数量，用于菜单角标或首页提醒。
     */
    @GetMapping("/messages/unread-count")
    public ApiResponse<Object> unreadCount() {
        CurrentUser user = UserContext.get();
        return ApiResponse.ok(Map.of("count", db.count("""
                select count(*) from user_message
                where recipient_id=? and read_at is null
                """, user.userId())));
    }

    /**
     * 把单条消息标记为已读。
     */
    @PatchMapping("/messages/{id}/read")
    public ApiResponse<Object> read(@PathVariable Long id) {
        CurrentUser user = UserContext.get();
        db.jdbc().update("""
                update user_message set read_at=coalesce(read_at, now())
                where message_id=? and recipient_id=?
                """, id, user.userId());
        return ApiResponse.ok(Map.of("messageId", id));
    }

    /**
     * 把当前用户全部未读消息标记为已读。
     */
    @PatchMapping("/messages/read-all")
    public ApiResponse<Object> readAll() {
        CurrentUser user = UserContext.get();
        db.jdbc().update("""
                update user_message set read_at=coalesce(read_at, now())
                where recipient_id=? and read_at is null
                """, user.userId());
        return ApiResponse.ok(Map.of("readAll", true));
    }

    /**
     * 管理员查看历史公告。
     */
    @GetMapping("/admin/notices")
    public ApiResponse<Object> notices() {
        requireAdmin();
        return ApiResponse.ok(db.jdbc().queryForList("""
                select n.*, u.real_name publisher_name,
                       (select count(*) from user_message m where m.source_notice_id=n.notice_id) receiver_count
                from notice n
                join user_account u on n.publisher_id = u.user_id
                order by n.published_at desc
                """));
    }

    /**
     * 管理员发布面向全部用户或某个主角色的公告。
     */
    @PostMapping("/admin/notices")
    public ApiResponse<Object> publish(@RequestBody NoticeRequest request) {
        CurrentUser admin = requireAdmin();
        Long id = messageService.publishNotice(
                admin.userId(),
                Db.require(request.title(), "title"),
                Db.require(request.content(), "content"),
                request.targetRole() == null ? NoticeTargetRole.ALL : request.targetRole(),
                request.priority() == null ? NoticePriority.NORMAL : request.priority()
        );
        return ApiResponse.ok(Map.of("noticeId", id));
    }

    /**
     * 停用已发布公告。历史收件箱消息仍保留，方便追溯。
     */
    @PatchMapping("/admin/notices/{id}/disable")
    public ApiResponse<Object> disableNotice(@PathVariable Long id) {
        requireAdmin();
        db.jdbc().update("update notice set notice_status='DISABLED' where notice_id=?", id);
        return ApiResponse.ok(Map.of("noticeId", id));
    }

    /**
     * 读取当前用户并校验管理员权限。
     */
    private CurrentUser requireAdmin() {
        CurrentUser user = UserContext.get();
        if (!user.isAdmin()) {
            throw new BusinessException("只有管理员可以执行该操作");
        }
        return user;
    }
}
