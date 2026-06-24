package com.example.campus.feedback;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.FeedbackRequests.FeedbackReplyRequest;
import com.example.campus.dto.FeedbackRequests.FeedbackRequest;
import com.example.campus.dto.FeedbackRequests.FeedbackStatusRequest;
import com.example.campus.enums.FeedbackStatus;
import com.example.campus.enums.MessageCategory;
import com.example.campus.message.MessageService;
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
 * 活动评价反馈接口。
 *
 * 学生在完成活动后提交评分和文字反馈；
 * 组织负责人管理自己组织活动的评价，管理员可以查看和处理全部评价。
 */
@RestController
@RequestMapping("/api")
public class FeedbackController {
    private final Db db;
    private final MessageService messageService;

    /**
     * 注入数据库工具和消息服务。
     */
    public FeedbackController(Db db, MessageService messageService) {
        this.db = db;
        this.messageService = messageService;
    }

    /**
     * 查询某个活动公开展示的可见评价。
     */
    @GetMapping("/activities/{activityId}/feedbacks")
    public ApiResponse<Object> activityFeedbacks(@PathVariable Long activityId) {
        return ApiResponse.ok(db.jdbc().queryForList("""
                select f.feedback_id, f.activity_id, f.rating, f.content, f.anonymous,
                       case when f.anonymous then '匿名用户' else u.real_name end display_name,
                       f.reply_content, replier.real_name replier_name, f.replied_at,
                       f.created_at
                from activity_feedback f
                join user_account u on f.user_id = u.user_id
                left join user_account replier on f.replied_by = replier.user_id
                where f.activity_id=? and f.feedback_status='VISIBLE'
                order by f.created_at desc
                """, activityId));
    }

    /**
     * 当前用户为完成并签到的活动提交评价。
     */
    @PostMapping("/activities/{activityId}/feedbacks")
    public ApiResponse<Object> create(@PathVariable Long activityId, @RequestBody FeedbackRequest request) {
        CurrentUser user = UserContext.get();
        Map<String, Object> activity = db.one("""
                select a.*, o.principal_user_id
                from activity a
                join organization o on a.org_id = o.org_id
                where a.activity_id=?
                """, activityId);
        if (!"FINISHED".equals(String.valueOf(activity.get("activity_status")))) {
            throw new BusinessException("活动结束后才能评价");
        }
        if (db.count("""
                select count(*) from registration
                where activity_id=? and user_id=? and registration_status='VALID' and checkin_status='CHECKED'
                """, activityId, user.userId()) == 0) {
            throw new BusinessException("只有已签到参与该活动的用户可以评价");
        }
        Integer rating = Db.require(request.rating(), "rating");
        if (rating < 1 || rating > 5) {
            throw new BusinessException("评分必须在 1 到 5 之间");
        }

        Long id = db.insert("""
                insert into activity_feedback(activity_id, user_id, rating, content, anonymous)
                values(?, ?, ?, ?, ?)
                """, activityId, user.userId(), rating,
                Db.require(request.content(), "content"), Boolean.TRUE.equals(request.anonymous()));
        messageService.notifyUser(
                ((Number) activity.get("principal_user_id")).longValue(),
                "收到新的活动评价",
                user.realName() + " 评价了活动“" + activity.get("activity_name") + "”。",
                MessageCategory.FEEDBACK,
                "ACTIVITY_FEEDBACK",
                id
        );
        return ApiResponse.ok(Map.of("feedbackId", id));
    }

    /**
     * 查询当前用户自己提交的活动评价。
     */
    @GetMapping("/feedbacks/mine")
    public ApiResponse<Object> mine() {
        CurrentUser user = UserContext.get();
        return ApiResponse.ok(db.jdbc().queryForList(feedbackSql("where f.user_id=?"), user.userId()));
    }

    /**
     * 管理员或组织负责人查询可管理的评价。
     */
    @GetMapping("/feedbacks/manage")
    public ApiResponse<Object> manage(@RequestParam(required = false) FeedbackStatus status) {
        CurrentUser user = UserContext.get();
        String statusCondition = status == null ? "" : " and f.feedback_status=?";
        if (user.isAdmin()) {
            Object[] args = status == null ? new Object[]{} : new Object[]{status.name()};
            return ApiResponse.ok(db.jdbc().queryForList(feedbackSql("where 1=1" + statusCondition), args));
        }
        if (user.isLeader()) {
            Object[] args = status == null ? new Object[]{user.userId()} : new Object[]{user.userId(), status.name()};
            return ApiResponse.ok(db.jdbc().queryForList(feedbackSql("where o.principal_user_id=?" + statusCondition), args));
        }
        throw new BusinessException("只有管理员或组织负责人可以管理评价");
    }

    /**
     * 管理员或组织负责人回复评价。
     */
    @PatchMapping("/feedbacks/{id}/reply")
    public ApiResponse<Object> reply(@PathVariable Long id, @RequestBody FeedbackReplyRequest request) {
        CurrentUser user = UserContext.get();
        Map<String, Object> feedback = loadFeedbackForManage(id);
        db.jdbc().update("""
                update activity_feedback
                set reply_content=?, replied_by=?, replied_at=now()
                where feedback_id=?
                """, Db.require(request.replyContent(), "replyContent"), user.userId(), id);
        messageService.notifyUser(
                ((Number) feedback.get("user_id")).longValue(),
                "你的活动评价已收到回复",
                "活动“" + feedback.get("activity_name") + "”的评价已由负责人或管理员回复。",
                MessageCategory.FEEDBACK,
                "ACTIVITY_FEEDBACK",
                id
        );
        return ApiResponse.ok(Map.of("feedbackId", id));
    }

    /**
     * 管理员或组织负责人隐藏或恢复评价展示。
     */
    @PatchMapping("/feedbacks/{id}/status")
    public ApiResponse<Object> status(@PathVariable Long id, @RequestBody FeedbackStatusRequest request) {
        FeedbackStatus status = Db.require(request.status(), "status");
        loadFeedbackForManage(id);
        db.jdbc().update("update activity_feedback set feedback_status=? where feedback_id=?", status.name(), id);
        return ApiResponse.ok(Map.of("feedbackId", id, "status", status.name()));
    }

    /**
     * 加载评价并校验当前用户是否有管理权限。
     */
    private Map<String, Object> loadFeedbackForManage(Long id) {
        CurrentUser user = UserContext.get();
        Map<String, Object> feedback = db.one("""
                select f.*, a.activity_name, a.org_id, o.principal_user_id
                from activity_feedback f
                join activity a on f.activity_id = a.activity_id
                join organization o on a.org_id = o.org_id
                where f.feedback_id=?
                """, id);
        if (user.isAdmin()) {
            return feedback;
        }
        if (user.isLeader() && user.userId().equals(((Number) feedback.get("principal_user_id")).longValue())) {
            return feedback;
        }
        throw new BusinessException("你没有权限管理该评价");
    }

    /**
     * 评价列表共用 SQL。
     */
    private String feedbackSql(String where) {
        String whereClause = where == null || where.isBlank() ? "" : where.strip() + "\n";
        return """
                select f.*, a.activity_name, a.start_time, o.org_name,
                       u.student_no, u.real_name user_name,
                       replier.real_name replier_name
                from activity_feedback f
                join activity a on f.activity_id = a.activity_id
                join organization o on a.org_id = o.org_id
                join user_account u on f.user_id = u.user_id
                left join user_account replier on f.replied_by = replier.user_id
                """ + whereClause + """
                order by f.created_at desc, f.feedback_id desc
                """;
    }
}
