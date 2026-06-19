package com.example.campus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/activities")
public class ActivityController {
    private final Db db;

    public ActivityController(Db db) {
        this.db = db;
    }

    @GetMapping
    public ApiResponse<Object> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Long typeId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) Long orgId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.add("(a.activity_name like :kw or a.description like :kw or o.org_name like :kw)");
            params.addValue("kw", "%" + keyword + "%");
        }
        if (typeId != null) {
            where.add("a.type_id = :typeId");
            params.addValue("typeId", typeId);
        }
        if (status != null && !status.isBlank()) {
            where.add("a.activity_status = :status");
            params.addValue("status", status);
        }
        if (orgId != null) {
            where.add("a.org_id = :orgId");
            params.addValue("orgId", orgId);
        }
        String sql = """
                select a.*, t.type_name, o.org_name, u.real_name creator_name,
                       coalesce(a.base_score_override, sr.base_score) base_score,
                       exists(select 1 from registration r where r.activity_id=a.activity_id and r.user_id=:currentUser and r.registration_status='VALID') registered
                from activity a
                join activity_type t on a.type_id = t.type_id
                join organization o on a.org_id = o.org_id
                join user_account u on a.created_by = u.user_id
                left join score_rule sr on a.rule_id = sr.rule_id
                """;
        params.addValue("currentUser", UserContext.get().userId());
        if (!where.isEmpty()) {
            sql += " where " + String.join(" and ", where);
        }
        sql += " order by a.start_time desc";
        return ApiResponse.ok(db.named().queryForList(sql, params));
    }

    @GetMapping("/{id}")
    public ApiResponse<Object> detail(@PathVariable Long id) {
        CurrentUser user = UserContext.get();
        Map<String, Object> activity = db.one("""
                select a.*, t.type_name, o.org_name, o.principal_user_id, sr.base_score,
                       coalesce(a.base_score_override, sr.base_score) base_score,
                       sr.normal_weight, sr.member_weight, sr.leader_weight,
                       exists(select 1 from registration r where r.activity_id=a.activity_id and r.user_id=? and r.registration_status='VALID') registered
                from activity a
                join activity_type t on a.type_id = t.type_id
                join organization o on a.org_id = o.org_id
                left join score_rule sr on a.rule_id = sr.rule_id
                where a.activity_id = ?
                """, user.userId(), id);
        return ApiResponse.ok(activity);
    }

    @PostMapping
    public ApiResponse<Object> create(@RequestBody Map<String, Object> body) {
        CurrentUser user = UserContext.get();
        requireLeaderOrAdmin();
        Long orgId = Db.longVal(body, "orgId");
        ensureCanManageOrg(orgId);
        LocalDateTime start = Db.dateTime(body, "startTime");
        LocalDateTime end = Db.dateTime(body, "endTime");
        LocalDateTime deadline = Db.dateTime(body, "registrationDeadline");
        validateTime(start, end, deadline);
        Long ruleId = Db.longVal(body, "ruleId");
        if (ruleId == null) {
            Map<String, Object> rule = db.one("select rule_id from score_rule where type_id=? and effective_status='ENABLED' limit 1", Db.longVal(body, "typeId"));
            ruleId = ((Number) rule.get("rule_id")).longValue();
        }
        Long id = db.insert("""
                insert into activity(activity_name, type_id, org_id, start_time, end_time, location,
                    registration_deadline, capacity, description, requirement, base_score_override, rule_id, activity_status, created_by)
                values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Db.required(body, "activityName"), Db.longVal(body, "typeId"), orgId,
                Db.ts(start), Db.ts(end), Db.required(body, "location"), Db.ts(deadline),
                Db.intVal(body, "capacity"), Db.str(body, "description"), Db.str(body, "requirement"),
                Db.doubleVal(body, "baseScore"), ruleId,
                Db.str(body, "activityStatus") == null ? "DRAFT" : Db.str(body, "activityStatus"), user.userId());
        return ApiResponse.ok(Map.of("activityId", id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> old = db.one("select * from activity where activity_id=?", id);
        ensureCanManageOrg(((Number) old.get("org_id")).longValue());
        LocalDateTime start = Db.dateTime(body, "startTime");
        LocalDateTime end = Db.dateTime(body, "endTime");
        LocalDateTime deadline = Db.dateTime(body, "registrationDeadline");
        validateTime(start, end, deadline);
        db.jdbc().update("""
                update activity set activity_name=?, type_id=?, org_id=?, start_time=?, end_time=?, location=?,
                    registration_deadline=?, capacity=?, description=?, requirement=?, base_score_override=?, rule_id=?
                where activity_id=?
                """,
                Db.required(body, "activityName"), Db.longVal(body, "typeId"), Db.longVal(body, "orgId"),
                Db.ts(start), Db.ts(end), Db.required(body, "location"), Db.ts(deadline),
                Db.intVal(body, "capacity"), Db.str(body, "description"), Db.str(body, "requirement"),
                Db.doubleVal(body, "baseScore"), Db.longVal(body, "ruleId"), id);
        return ApiResponse.ok(Map.of("activityId", id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Object> status(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> old = db.one("select * from activity where activity_id=?", id);
        ensureCanManageOrg(((Number) old.get("org_id")).longValue());
        String next = Db.required(body, "status");
        List<String> allowed = switch (String.valueOf(old.get("activity_status"))) {
            case "DRAFT" -> List.of("OPEN", "OFFLINE");
            case "OPEN" -> List.of("CLOSED", "FINISHED", "OFFLINE");
            case "CLOSED" -> List.of("FINISHED", "OFFLINE");
            case "FINISHED" -> List.of("OFFLINE");
            default -> List.of();
        };
        if (!allowed.contains(next)) {
            throw new BusinessException("活动状态不允许回退或非法流转");
        }
        db.jdbc().update("update activity set activity_status=? where activity_id=?", next, id);
        return ApiResponse.ok(Map.of("activityId", id, "status", next));
    }

    @PostMapping("/{id}/register")
    public ApiResponse<Object> register(@PathVariable Long id) {
        CurrentUser user = UserContext.get();
        Map<String, Object> activity = db.one("select * from activity where activity_id=?", id);
        if (!"OPEN".equals(String.valueOf(activity.get("activity_status")))) {
            throw new BusinessException("活动当前不可报名");
        }
        LocalDateTime deadline = Db.localDateTime(activity.get("registration_deadline"));
        if (deadline == null) {
            throw new BusinessException("活动报名截止时间缺失");
        }
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException("已超过报名截止时间");
        }
        if (((Number) activity.get("registered_count")).intValue() >= ((Number) activity.get("capacity")).intValue()) {
            throw new BusinessException("活动名额已满");
        }
        Map<String, Object> old = db.maybeOne("select * from registration where activity_id=? and user_id=?", id, user.userId());
        if (old != null) {
            if ("VALID".equals(String.valueOf(old.get("registration_status")))) {
                throw new BusinessException("你已经报名过该活动");
            }
            db.jdbc().update("""
                    update registration set registration_status='VALID', checkin_status='NOT_CHECKED', registered_at=now()
                    where activity_id=? and user_id=?
                    """, id, user.userId());
        } else {
            db.insert("insert into registration(activity_id, user_id) values(?, ?)", id, user.userId());
        }
        db.jdbc().update("""
                update activity set registered_count = (
                    select count(*) from registration where activity_id=? and registration_status='VALID'
                ) where activity_id=?
                """, id, id);
        return ApiResponse.ok(Map.of("activityId", id));
    }

    @DeleteMapping("/{id}/register")
    public ApiResponse<Object> cancel(@PathVariable Long id) {
        CurrentUser user = UserContext.get();
        Map<String, Object> activity = db.one("select * from activity where activity_id=?", id);
        if (!List.of("OPEN", "CLOSED").contains(String.valueOf(activity.get("activity_status")))) {
            throw new BusinessException("当前活动阶段不允许取消报名");
        }
        db.jdbc().update("""
                update registration set registration_status='CANCELLED'
                where activity_id=? and user_id=? and registration_status='VALID'
                """, id, user.userId());
        db.jdbc().update("""
                update activity set registered_count = (
                    select count(*) from registration where activity_id=? and registration_status='VALID'
                ) where activity_id=?
                """, id, id);
        return ApiResponse.ok(Map.of("activityId", id));
    }

    @GetMapping("/{id}/registrations")
    public ApiResponse<Object> registrations(@PathVariable Long id) {
        Map<String, Object> activity = db.one("select org_id from activity where activity_id=?", id);
        ensureCanManageOrg(((Number) activity.get("org_id")).longValue());
        return ApiResponse.ok(db.jdbc().queryForList("""
                select r.*, u.student_no, u.real_name, sp.college, sp.major, sp.class_name
                from registration r
                join user_account u on r.user_id = u.user_id
                left join student_profile sp on u.user_id = sp.user_id
                where r.activity_id = ?
                order by r.registered_at desc
                """, id));
    }

    @PatchMapping("/{id}/registrations/{registrationId}/checkin")
    public ApiResponse<Object> checkin(@PathVariable Long id, @PathVariable Long registrationId, @RequestBody Map<String, Object> body) {
        Map<String, Object> activity = db.one("select org_id from activity where activity_id=?", id);
        ensureCanManageOrg(((Number) activity.get("org_id")).longValue());
        db.jdbc().update("update registration set checkin_status=? where registration_id=? and activity_id=?",
                Db.required(body, "checkinStatus"), registrationId, id);
        return ApiResponse.ok(Map.of("registrationId", registrationId));
    }

    private void validateTime(LocalDateTime start, LocalDateTime end, LocalDateTime deadline) {
        if (start == null || end == null || deadline == null) {
            throw new BusinessException("活动开始、结束和报名截止时间不能为空");
        }
        if (end.isBefore(start)) {
            throw new BusinessException("活动结束时间不能早于开始时间");
        }
        if (deadline.isAfter(start)) {
            throw new BusinessException("报名截止时间不能晚于活动开始时间");
        }
    }

    private void requireLeaderOrAdmin() {
        CurrentUser user = UserContext.get();
        if (!user.isAdmin() && !user.isLeader()) {
            throw new BusinessException("需要组织负责人或管理员权限");
        }
    }

    private void ensureCanManageOrg(Long orgId) {
        CurrentUser user = UserContext.get();
        if (user.isAdmin()) {
            return;
        }
        if (!user.isLeader()) {
            throw new BusinessException("需要组织负责人权限");
        }
        if (orgId == null || db.count("""
                select count(*) from organization
                where org_id=? and principal_user_id=? and org_status='ACTIVE'
                """, orgId, user.userId()) == 0) {
            throw new BusinessException("只能管理自己负责的有效组织");
        }
    }
}
