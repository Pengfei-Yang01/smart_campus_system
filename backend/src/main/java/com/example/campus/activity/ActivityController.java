package com.example.campus.activity;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.ActivityRequests.ActivityRequest;
import com.example.campus.dto.ActivityRequests.ActivityStatusRequest;
import com.example.campus.dto.ActivityRequests.CheckinRequest;
import com.example.campus.enums.ActivityStatus;
import com.example.campus.enums.CheckinStatus;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.UserContext;
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

/**
 * 活动发布、查询、报名和签到相关接口。
 */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {
    private final Db db;

    /**
     * 注入活动模块使用的数据库工具类。
     */
    public ActivityController(Db db) {
        this.db = db;
    }

    /**
     * 按关键字、类型、状态和组织等条件查询活动列表。
     */
    @GetMapping
    public ApiResponse<Object> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Long typeId,
                                    @RequestParam(required = false) ActivityStatus status,
                                    @RequestParam(required = false) Long orgId) {
        CurrentUser user = UserContext.get();
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            where.add("(a.activity_name like :kw or a.description like :kw or o.org_name like :kw)");
            params.addValue("kw", "%" + keyword.trim() + "%");
        }
        if (typeId != null) {
            where.add("a.type_id = :typeId");
            params.addValue("typeId", typeId);
        }
        if (status != null) {
            where.add("a.activity_status = :status");
            params.addValue("status", status.name());
        }
        if (orgId != null) {
            where.add("a.org_id = :orgId");
            params.addValue("orgId", orgId);
        }

        // 列表查询会关联活动类型、组织和创建人姓名，
        // 前端渲染列表时不需要再额外请求这些名称。
        String sql = """
                select a.*, t.type_name, o.org_name, o.org_status, u.real_name creator_name,
                       coalesce(a.base_score_override, sr.base_score) base_score,
                       exists(select 1 from registration r
                              where r.activity_id=a.activity_id and r.user_id=:currentUser
                                and r.registration_status='VALID') registered
                from activity a
                join activity_type t on a.type_id = t.type_id
                join organization o on a.org_id = o.org_id
                join user_account u on a.created_by = u.user_id
                left join score_rule sr on a.rule_id = sr.rule_id
                """;
        params.addValue("currentUser", user.userId());
        if (!where.isEmpty()) {
            sql += " where " + String.join(" and ", where);
        }
        sql += " order by a.start_time desc";
        return ApiResponse.ok(db.named().queryForList(sql, params));
    }

    /**
     * 查询单个活动详情，同时返回积分规则权重和当前用户的
     * 报名状态。
     */
    @GetMapping("/{id}")
    public ApiResponse<Object> detail(@PathVariable Long id) {
        if (db.count("""
        select count(*)
        from activity
        where activity_id = ?
        """, id) == 0) {
        throw new BusinessException("活动不存在");
        }
        CurrentUser user = UserContext.get();
        Map<String, Object> activity = db.one("""
                select a.*, t.type_name, o.org_name, o.org_status, o.principal_user_id,
                       coalesce(a.base_score_override, sr.base_score) base_score,
                       sr.normal_weight, sr.member_weight, sr.leader_weight,
                       exists(select 1 from registration r
                              where r.activity_id=a.activity_id and r.user_id=?
                                and r.registration_status='VALID') registered
                from activity a
                join activity_type t on a.type_id = t.type_id
                join organization o on a.org_id = o.org_id
                left join score_rule sr on a.rule_id = sr.rule_id
                where a.activity_id = ?
                """, user.userId(), id);
        return ApiResponse.ok(activity);
    }

    /**
     * 创建活动。组织负责人只能在自己负责的
     * 有效组织下发布活动，管理员可以在任意组织下发布。
     */
    @PostMapping
    public ApiResponse<Object> create(@RequestBody ActivityRequest request) {
        CurrentUser user = UserContext.get();
        requireLeaderOrAdmin();

        Long typeId = Db.require(request.typeId(), "typeId");
        Long orgId = Db.require(request.orgId(), "orgId");
        ensureCanManageOrg(orgId);
        requireActiveOrganization(orgId);
        Integer capacity = requirePositiveCapacity(request.capacity());
        LocalDateTime start = Db.dateTime(request.startTime(), "startTime");
        LocalDateTime end = Db.dateTime(request.endTime(), "endTime");
        LocalDateTime deadline = Db.dateTime(request.registrationDeadline(), "registrationDeadline");
        validateTime(start, end, deadline);
        Long ruleId = resolveRuleId(typeId, request.ruleId());
        ActivityStatus status = request.activityStatus() == null ? ActivityStatus.DRAFT : request.activityStatus();

        Long id = db.insert("""
                insert into activity(activity_name, type_id, org_id, start_time, end_time, location,
                    registration_deadline, capacity, description, requirement, base_score_override,
                    rule_id, activity_status, created_by)
                values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Db.require(request.activityName(), "activityName"), typeId, orgId,
                Db.ts(start), Db.ts(end), Db.require(request.location(), "location"), Db.ts(deadline),
                capacity, request.description(), request.requirement(),
                request.baseScore(), ruleId, status.name(), user.userId());
        return ApiResponse.ok(Map.of("activityId", id));
    }

    /**
     * 更新已有活动信息，但不改变活动当前生命周期状态。
     */
    @PutMapping("/{id}")
    public ApiResponse<Object> update(@PathVariable Long id, @RequestBody ActivityRequest request) {
        Map<String, Object> old = db.one("select * from activity where activity_id=?", id);
        ensureCanManageOrg(((Number) old.get("org_id")).longValue());

        Long typeId = Db.require(request.typeId(), "typeId");
        Long orgId = Db.require(request.orgId(), "orgId");
        ensureCanManageOrg(orgId);
        requireActiveOrganization(orgId);
        Integer capacity = requirePositiveCapacity(request.capacity());
        LocalDateTime start = Db.dateTime(request.startTime(), "startTime");
        LocalDateTime end = Db.dateTime(request.endTime(), "endTime");
        LocalDateTime deadline = Db.dateTime(request.registrationDeadline(), "registrationDeadline");
        validateTime(start, end, deadline);

        db.jdbc().update("""
                update activity set activity_name=?, type_id=?, org_id=?, start_time=?, end_time=?, location=?,
                    registration_deadline=?, capacity=?, description=?, requirement=?, base_score_override=?, rule_id=?
                where activity_id=?
                """,
                Db.require(request.activityName(), "activityName"), typeId, orgId,
                Db.ts(start), Db.ts(end), Db.require(request.location(), "location"), Db.ts(deadline),
                capacity, request.description(), request.requirement(),
                request.baseScore(), resolveRuleId(typeId, request.ruleId()), id);
        return ApiResponse.ok(Map.of("activityId", id));
    }

    /**
     * 按照允许的活动生命周期规则切换状态。
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<Object> status(@PathVariable Long id, @RequestBody ActivityStatusRequest request) {
        Map<String, Object> old = db.one("""
                select a.*, o.org_status
                from activity a join organization o on a.org_id = o.org_id
                where a.activity_id=?
                """, id);
        ensureCanManageOrg(((Number) old.get("org_id")).longValue());

        ActivityStatus current = ActivityStatus.valueOf(String.valueOf(old.get("activity_status")));
        ActivityStatus next = Db.require(request.status(), "status");

        // 这里限制活动状态跳转，避免调用方从草稿直接跳到结束，
        // 也避免已下架活动被重新打开。
        List<ActivityStatus> allowed = switch (current) {
            case DRAFT -> List.of(ActivityStatus.OPEN, ActivityStatus.OFFLINE);
            case OPEN -> List.of(ActivityStatus.CLOSED, ActivityStatus.FINISHED, ActivityStatus.OFFLINE);
            case CLOSED -> List.of(ActivityStatus.FINISHED, ActivityStatus.OFFLINE);
            case FINISHED -> List.of(ActivityStatus.OFFLINE);
            case OFFLINE -> List.of();
        };
        if (!allowed.contains(next)) {
            throw new BusinessException("当前活动状态不允许切换到目标状态");
        }
        if (!"ACTIVE".equals(String.valueOf(old.get("org_status"))) && next != ActivityStatus.OFFLINE) {
            throw new BusinessException("活动所属组织已停用，只能下架活动");
        }

        db.jdbc().update("update activity set activity_status=? where activity_id=?", next.name(), id);
        return ApiResponse.ok(Map.of("activityId", id, "status", next.name()));
    }

    /**
     * 为当前登录用户报名开放中的活动。
     */
    @PostMapping("/{id}/register")
    public ApiResponse<Object> register(@PathVariable Long id) {
        CurrentUser user = UserContext.get();
        Map<String, Object> activity = db.one("""
                select a.*, o.org_status
                from activity a join organization o on a.org_id = o.org_id
                where a.activity_id=?
                """, id);

        if (!"ACTIVE".equals(String.valueOf(activity.get("org_status")))) {
            throw new BusinessException("活动所属组织已停用，不能报名");
        }
        if (!ActivityStatus.OPEN.name().equals(String.valueOf(activity.get("activity_status")))) {
            throw new BusinessException("活动未开放报名");
        }
        LocalDateTime deadline = Db.localDateTime(activity.get("registration_deadline"));
        if (deadline == null) {
            throw new BusinessException("活动报名截止时间异常");
        }
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException("报名时间已截止");
        }
        if (((Number) activity.get("registered_count")).intValue() >= ((Number) activity.get("capacity")).intValue()) {
            throw new BusinessException("活动名额已满");
        }

        Map<String, Object> old = db.maybeOne("select * from registration where activity_id=? and user_id=?", id, user.userId());
        if (old != null) {
            if ("VALID".equals(String.valueOf(old.get("registration_status")))) {
                throw new BusinessException("你已报名该活动");
            }
            db.jdbc().update("""
                    update registration set registration_status='VALID', checkin_status='NOT_CHECKED', registered_at=now()
                    where activity_id=? and user_id=?
                    """, id, user.userId());
        } else {
            db.insert("insert into registration(activity_id, user_id) values(?, ?)", id, user.userId());
        }
        refreshRegisteredCount(id);
        return ApiResponse.ok(Map.of("activityId", id));
    }

    /**
     * 在活动结束前取消当前用户的报名。
     */
    @DeleteMapping("/{id}/register")
    public ApiResponse<Object> cancel(@PathVariable Long id) {
        CurrentUser user = UserContext.get();
        Map<String, Object> activity = db.one("select * from activity where activity_id=?", id);
        if (!List.of(ActivityStatus.OPEN.name(), ActivityStatus.CLOSED.name())
                .contains(String.valueOf(activity.get("activity_status")))) {
            throw new BusinessException("当前活动状态不允许取消报名");
        }

        db.jdbc().update("""
                update registration set registration_status='CANCELLED'
                where activity_id=? and user_id=? and registration_status='VALID'
                """, id, user.userId());
        refreshRegisteredCount(id);
        return ApiResponse.ok(Map.of("activityId", id));
    }

    /**
     * 给组织管理者查询某个活动的全部报名记录。
     */
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

    /**
     * 更新某条报名记录的签到结果。
     */
    @PatchMapping("/{id}/registrations/{registrationId}/checkin")
    public ApiResponse<Object> checkin(@PathVariable Long id,
                                       @PathVariable Long registrationId,
                                       @RequestBody CheckinRequest request) {
        Map<String, Object> activity = db.one("select org_id from activity where activity_id=?", id);
        ensureCanManageOrg(((Number) activity.get("org_id")).longValue());
        CheckinStatus status = Db.require(request.checkinStatus(), "checkinStatus");
        db.jdbc().update("update registration set checkin_status=? where registration_id=? and activity_id=?",
                status.name(), registrationId, id);
        return ApiResponse.ok(Map.of("registrationId", registrationId));
    }

    /**
     * 如果请求里传入规则编号就使用该规则，否则选择当前活动类型下启用的
     * 默认积分规则。
     */
    private Long resolveRuleId(Long typeId, Long requestedRuleId) {
        if (requestedRuleId != null) {
            return requestedRuleId;
        }
        Map<String, Object> rule = db.one("""
                select rule_id from score_rule
                where type_id=? and effective_status='ENABLED'
                limit 1
                """, typeId);
        return ((Number) rule.get("rule_id")).longValue();
    }

    /**
     * 报名或取消报名后，重新计算活动表中的冗余报名人数。
     */
    private void refreshRegisteredCount(Long activityId) {
        db.jdbc().update("""
                update activity set registered_count = (
                    select count(*) from registration where activity_id=? and registration_status='VALID'
                ) where activity_id=?
                """, activityId, activityId);
    }

    /**
     * 校验数据库和前端都要求满足的活动时间关系。
     */
    private void validateTime(LocalDateTime start, LocalDateTime end, LocalDateTime deadline) {
        if (end.isBefore(start)) {
            throw new BusinessException("活动结束时间不能早于开始时间");
        }
        if (deadline.isAfter(start)) {
            throw new BusinessException("报名截止时间不能晚于活动开始时间");
        }
    }

    /**
     * 校验活动容量，确保满足数据库表约束。
     */
    private Integer requirePositiveCapacity(Integer capacity) {
        Integer value = Db.require(capacity, "capacity");
        if (value <= 0) {
            throw new BusinessException("活动容量必须大于 0");
        }
        return value;
    }

    /**
     * 仅允许管理员和组织负责人发布活动。
     */
    private void requireLeaderOrAdmin() {
        CurrentUser user = UserContext.get();
        if (!user.isAdmin() && !user.isLeader()) {
            throw new BusinessException("只有管理员或组织负责人可以发布活动");
        }
    }

    /**
     * 校验组织负责人只能管理自己负责的有效组织，
     * 管理员则可以管理任意组织。
     */
    private void ensureCanManageOrg(Long orgId) {
        CurrentUser user = UserContext.get();
        if (user.isAdmin()) {
            return;
        }
        if (!user.isLeader()) {
            throw new BusinessException("只有管理员或组织负责人可以管理组织活动");
        }
        if (orgId == null || db.count("""
                select count(*) from organization
                where org_id=? and principal_user_id=? and org_status='ACTIVE'
                """, orgId, user.userId()) == 0) {
            throw new BusinessException("你只能管理自己负责的有效组织");
        }
    }

    /**
     * 发布或迁移活动时，所有角色都必须选择启用中的组织。
     */
    private void requireActiveOrganization(Long orgId) {
        if (orgId == null || db.count("select count(*) from organization where org_id=? and org_status='ACTIVE'", orgId) == 0) {
            throw new BusinessException("组织已停用，不能发布或更新活动");
        }
    }
}
