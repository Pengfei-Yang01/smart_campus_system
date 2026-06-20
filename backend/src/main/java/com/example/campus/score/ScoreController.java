package com.example.campus.score;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.ScoreRequests.ScoreAuditRequest;
import com.example.campus.dto.ScoreRequests.ScoreRecordRequest;
import com.example.campus.dto.ScoreRequests.ScoreRuleRequest;
import com.example.campus.enums.AuditStatus;
import com.example.campus.enums.EffectiveStatus;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.UserContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分记录、审核和积分规则接口。
 */
@RestController
@RequestMapping("/api/scores")
public class ScoreController {
    private final Db db;

    /**
     * 注入积分模块使用的数据库工具类。
     */
    public ScoreController(Db db) {
        this.db = db;
    }

    /**
     * 根据当前角色返回积分记录：
     * 管理员查看全部记录，负责人查看自己
     * 负责组织的记录，学生只查看自己的记录。
     */
    @GetMapping
    public ApiResponse<Object> list(@RequestParam(required = false) AuditStatus status) {
        CurrentUser user = UserContext.get();
        String condition = status == null ? "" : " and sr.audit_status = ?";
        Object[] args = status == null ? new Object[]{} : new Object[]{status.name()};

        if (user.isAdmin()) {
            return ApiResponse.ok(db.jdbc().queryForList(scoreSql("where 1=1" + condition), args));
        }
        if (user.isLeader()) {
            Object[] leaderArgs = status == null ? new Object[]{user.userId()} : new Object[]{user.userId(), status.name()};
            return ApiResponse.ok(db.jdbc().queryForList(scoreSql("where o.principal_user_id = ?" + condition), leaderArgs));
        }
        Object[] studentArgs = status == null ? new Object[]{user.userId()} : new Object[]{user.userId(), status.name()};
        return ApiResponse.ok(db.jdbc().queryForList(scoreSql("where sr.user_id = ?" + condition), studentArgs));
    }

    /**
     * 活动结束后为学生录入积分。
     */
    @PostMapping
    public ApiResponse<Object> record(@RequestBody ScoreRecordRequest request) {
        CurrentUser submitter = UserContext.get();
        Long activityId = Db.require(request.activityId(), "activityId");
        Long userId = Db.require(request.userId(), "userId");

        Map<String, Object> activity = db.one("""
                select a.*, o.principal_user_id, sr.rule_id,
                       coalesce(a.base_score_override, sr.base_score) base_score,
                       sr.normal_weight, sr.member_weight, sr.leader_weight
                from activity a
                join organization o on a.org_id = o.org_id
                join score_rule sr on a.rule_id = sr.rule_id
                where a.activity_id=?
                """, activityId);
        if (!submitter.isAdmin() && !submitter.userId().equals(((Number) activity.get("principal_user_id")).longValue())) {
            throw new BusinessException("你没有权限为该活动录入积分");
        }
        if (!"FINISHED".equals(String.valueOf(activity.get("activity_status")))) {
            throw new BusinessException("活动结束后才能录入积分");
        }
        if (db.count("""
                select count(*) from registration
                where activity_id=? and user_id=? and registration_status='VALID'
                """, activityId, userId) == 0) {
            throw new BusinessException("该学生未有效报名活动，不能录入积分");
        }

        // 最终积分取决于学生与活动主办组织的关系：
        // 负责人 > 组织成员 > 普通参与者。
        String identity = "NORMAL";
        double weight = ((Number) activity.get("normal_weight")).doubleValue();
        if (userId.equals(((Number) activity.get("principal_user_id")).longValue())) {
            identity = "ORG_LEADER";
            weight = ((Number) activity.get("leader_weight")).doubleValue();
        } else if (db.count("""
                select count(*) from organization_member
                where org_id=? and user_id=? and join_status='APPROVED'
                """, activity.get("org_id"), userId) > 0) {
            identity = "ORG_MEMBER";
            weight = ((Number) activity.get("member_weight")).doubleValue();
        }

        double base = request.baseScore() == null
                ? ((Number) activity.get("base_score")).doubleValue()
                : request.baseScore();
        double finalScore = Math.round(base * weight * 100.0) / 100.0;
        Long scoreId = db.insert("""
                insert into score_record(user_id, activity_id, rule_id, identity_type, base_score,
                    identity_weight, final_score, audit_status, submitter_id)
                values(?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, userId, activityId, activity.get("rule_id"), identity, base, weight, finalScore, submitter.userId());
        return ApiResponse.ok(Map.of("scoreId", scoreId, "finalScore", finalScore, "identityType", identity));
    }

    /**
     * 管理员通过或驳回已提交的积分记录。
     */
    @PatchMapping("/{id}/audit")
    public ApiResponse<Object> audit(@PathVariable Long id, @RequestBody ScoreAuditRequest request) {
        CurrentUser user = requireAdmin();
        AuditStatus status = Db.require(request.auditStatus(), "auditStatus");
        if (status != AuditStatus.APPROVED && status != AuditStatus.REJECTED) {
            throw new BusinessException("积分审核状态只能是 APPROVED 或 REJECTED");
        }

        db.jdbc().update("""
                update score_record set audit_status=?, reviewer_id=?, reject_reason=?, reviewed_at=now()
                where score_id=?
                """, status.name(), user.userId(), request.rejectReason(), id);
        return ApiResponse.ok(Map.of("scoreId", id, "auditStatus", status.name()));
    }

    /**
     * 为指定活动类型创建积分规则。
     */
    @PostMapping("/rules")
    public ApiResponse<Object> createRule(@RequestBody ScoreRuleRequest request) {
        requireAdmin();
        Long id = db.insert("""
                insert into score_rule(type_id, base_score, normal_weight, member_weight, leader_weight, rule_desc, effective_status)
                values(?, ?, ?, ?, ?, ?, ?)
                """, Db.require(request.typeId(), "typeId"), Db.require(request.baseScore(), "baseScore"),
                Db.require(request.normalWeight(), "normalWeight"), Db.require(request.memberWeight(), "memberWeight"),
                Db.require(request.leaderWeight(), "leaderWeight"), request.ruleDesc(),
                (request.effectiveStatus() == null ? EffectiveStatus.ENABLED : request.effectiveStatus()).name());
        return ApiResponse.ok(Map.of("ruleId", id));
    }

    /**
     * 更新已有积分规则。
     */
    @PutMapping("/rules/{id}")
    public ApiResponse<Object> updateRule(@PathVariable Long id, @RequestBody ScoreRuleRequest request) {
        requireAdmin();
        db.jdbc().update("""
                update score_rule set base_score=?, normal_weight=?, member_weight=?, leader_weight=?, rule_desc=?, effective_status=?
                where rule_id=?
                """, Db.require(request.baseScore(), "baseScore"), Db.require(request.normalWeight(), "normalWeight"),
                Db.require(request.memberWeight(), "memberWeight"), Db.require(request.leaderWeight(), "leaderWeight"),
                request.ruleDesc(), Db.require(request.effectiveStatus(), "effectiveStatus").name(), id);
        return ApiResponse.ok(Map.of("ruleId", id));
    }

    /**
     * 积分列表页面共用的数据库查询片段。
     */
    private String scoreSql(String where) {
        return """
                select sr.*, u.student_no, u.real_name, a.activity_name, o.org_name, t.type_name
                from score_record sr
                join user_account u on sr.user_id = u.user_id
                join activity a on sr.activity_id = a.activity_id
                join organization o on a.org_id = o.org_id
                join activity_type t on a.type_id = t.type_id
                """ + where + " order by sr.submitted_at desc";
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
