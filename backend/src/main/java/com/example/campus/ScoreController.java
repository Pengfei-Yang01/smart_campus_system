package com.example.campus;

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

@RestController
@RequestMapping("/api/scores")
public class ScoreController {
    private final Db db;

    public ScoreController(Db db) {
        this.db = db;
    }

    @GetMapping
    public ApiResponse<Object> list(@RequestParam(required = false) String status) {
        CurrentUser user = UserContext.get();
        String condition = status == null || status.isBlank() ? "" : " and sr.audit_status = ?";
        Object[] args = condition.isBlank() ? new Object[]{} : new Object[]{status};
        if (user.isAdmin()) {
            return ApiResponse.ok(db.jdbc().queryForList(scoreSql("where 1=1" + condition), args));
        }
        if (user.isLeader()) {
            Object[] leaderArgs = condition.isBlank() ? new Object[]{user.userId()} : new Object[]{user.userId(), status};
            return ApiResponse.ok(db.jdbc().queryForList(scoreSql("where o.principal_user_id = ?" + condition), leaderArgs));
        }
        return ApiResponse.ok(db.jdbc().queryForList(scoreSql("where sr.user_id = ?" + condition),
                condition.isBlank() ? new Object[]{user.userId()} : new Object[]{user.userId(), status}));
    }

    @PostMapping
    public ApiResponse<Object> record(@RequestBody Map<String, Object> body) {
        CurrentUser submitter = UserContext.get();
        Long activityId = Db.longVal(body, "activityId");
        Long userId = Db.longVal(body, "userId");
        Map<String, Object> activity = db.one("""
                select a.*, o.principal_user_id, sr.rule_id, coalesce(a.base_score_override, sr.base_score) base_score,
                       sr.normal_weight, sr.member_weight, sr.leader_weight
                from activity a
                join organization o on a.org_id = o.org_id
                join score_rule sr on a.rule_id = sr.rule_id
                where a.activity_id=?
                """, activityId);
        if (!submitter.isAdmin() && !submitter.userId().equals(((Number) activity.get("principal_user_id")).longValue())) {
            throw new BusinessException("只能为自己负责组织的活动录分");
        }
        if (!"FINISHED".equals(String.valueOf(activity.get("activity_status")))) {
            throw new BusinessException("只有已结束活动可以录入积分");
        }
        if (db.count("""
                select count(*) from registration
                where activity_id=? and user_id=? and registration_status='VALID'
                """, activityId, userId) == 0) {
            throw new BusinessException("录分对象必须来自有效报名名单");
        }
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
        double base = Db.doubleVal(body, "baseScore") == null
                ? ((Number) activity.get("base_score")).doubleValue()
                : Db.doubleVal(body, "baseScore");
        double finalScore = Math.round(base * weight * 100.0) / 100.0;
        Long scoreId = db.insert("""
                insert into score_record(user_id, activity_id, rule_id, identity_type, base_score,
                    identity_weight, final_score, audit_status, submitter_id)
                values(?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, userId, activityId, activity.get("rule_id"), identity, base, weight, finalScore, submitter.userId());
        return ApiResponse.ok(Map.of("scoreId", scoreId, "finalScore", finalScore, "identityType", identity));
    }

    @PatchMapping("/{id}/audit")
    public ApiResponse<Object> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CurrentUser user = UserContext.get();
        if (!user.isAdmin()) {
            throw new BusinessException("只有管理员可以审核积分");
        }
        String status = Db.required(body, "auditStatus");
        if (!status.equals("APPROVED") && !status.equals("REJECTED")) {
            throw new BusinessException("积分审核状态只能为 APPROVED 或 REJECTED");
        }
        db.jdbc().update("""
                update score_record set audit_status=?, reviewer_id=?, reject_reason=?, reviewed_at=now()
                where score_id=?
                """, status, user.userId(), Db.str(body, "rejectReason"), id);
        return ApiResponse.ok(Map.of("scoreId", id, "auditStatus", status));
    }

    @PostMapping("/rules")
    public ApiResponse<Object> createRule(@RequestBody Map<String, Object> body) {
        requireAdmin();
        Long id = db.insert("""
                insert into score_rule(type_id, base_score, normal_weight, member_weight, leader_weight, rule_desc, effective_status)
                values(?, ?, ?, ?, ?, ?, ?)
                """, Db.longVal(body, "typeId"), Db.doubleVal(body, "baseScore"), Db.doubleVal(body, "normalWeight"),
                Db.doubleVal(body, "memberWeight"), Db.doubleVal(body, "leaderWeight"), Db.str(body, "ruleDesc"),
                Db.str(body, "effectiveStatus") == null ? "ENABLED" : Db.str(body, "effectiveStatus"));
        return ApiResponse.ok(Map.of("ruleId", id));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<Object> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireAdmin();
        db.jdbc().update("""
                update score_rule set base_score=?, normal_weight=?, member_weight=?, leader_weight=?, rule_desc=?, effective_status=?
                where rule_id=?
                """, Db.doubleVal(body, "baseScore"), Db.doubleVal(body, "normalWeight"),
                Db.doubleVal(body, "memberWeight"), Db.doubleVal(body, "leaderWeight"), Db.str(body, "ruleDesc"),
                Db.required(body, "effectiveStatus"), id);
        return ApiResponse.ok(Map.of("ruleId", id));
    }

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

    private void requireAdmin() {
        if (!UserContext.get().isAdmin()) {
            throw new BusinessException("需要管理员权限");
        }
    }
}
