package com.example.campus.admin;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.AdminRequests.AuditRequest;
import com.example.campus.dto.AdminRequests.OrganizationStatusRequest;
import com.example.campus.dto.StudentRequests.StudentUpdateRequest;
import com.example.campus.enums.AuditStatus;
import com.example.campus.enums.MessageCategory;
import com.example.campus.enums.OrganizationStatus;
import com.example.campus.message.MessageService;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.UserContext;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员端统计、学生、审核和组织管理接口。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final Db db;
    private final MessageService messageService;

    /**
     * 注入管理员模块使用的数据库工具类和消息服务。
     */
    public AdminController(Db db, MessageService messageService) {
        this.db = db;
        this.messageService = messageService;
    }

    /**
     * 返回管理员首页需要展示的统计数量。
     */
    @GetMapping("/stats")
    public ApiResponse<Object> stats() {
        requireAdmin();
        return ApiResponse.ok(Map.of(
                "studentCount", db.count("select count(*) from user_account"),
                "activityCount", db.count("select count(*) from activity"),
                "openActivityCount", db.count("select count(*) from activity where activity_status='OPEN'"),
                "organizationCount", db.count("select count(*) from organization"),
                "pendingLeaderApplyCount", db.count("select count(*) from leader_apply where status='PENDING'"),
                "pendingOrgApplyCount", db.count("select count(*) from organization_apply where status='PENDING'"),
                "pendingScoreCount", db.count("select count(*) from score_record where audit_status='PENDING'"),
                "pendingAffairCount", db.count("select count(*) from affair_application where status='PENDING'")
        ));
    }

    /**
     * 按用户名、学号或真实姓名搜索学生。
     */
    @GetMapping("/students")
    public ApiResponse<Object> students(@RequestParam(required = false) String keyword) {
        requireAdmin();
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.ok(db.jdbc().queryForList(studentSql("") + " order by u.user_id"));
        }
        String kw = "%" + keyword.trim() + "%";
        return ApiResponse.ok(db.jdbc().queryForList(
                studentSql("where u.username like ? or u.student_no like ? or u.real_name like ?") + " order by u.user_id",
                kw, kw, kw));
    }

    /**
     * 在管理员页面修改学生账号和学籍资料字段。
     */
    @PatchMapping("/students/{id}")
    public ApiResponse<Object> updateStudent(@PathVariable Long id, @RequestBody StudentUpdateRequest request) {
        requireAdmin();
        // 校验手机号：仅允许11位数字
        if (request.phone() != null && !request.phone().isBlank()
                && !request.phone().matches("^\\d{11}$")) {
            throw new BusinessException("电话号码必须为11位数字");
        }
        // 校验邮箱：仅允许合法邮箱格式
        if (request.email() != null && !request.email().isBlank()
                && !request.email().matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new BusinessException("邮箱格式不正确");
        }
        db.jdbc().update("""
                update user_account set real_name=?, phone=?, email=?, account_status=?
                where user_id=?
                """, Db.require(request.realName(), "realName"), request.phone(), request.email(),
                Db.require(request.accountStatus(), "accountStatus").name(), id);
        db.jdbc().update("""
                update student_profile set college=?, major=?, class_name=?, grade=?
                where user_id=?
                """, request.college(), request.major(), request.className(), request.grade(), id);
        return ApiResponse.ok(Map.of("userId", id));
    }

    /**
     * 返回学生完整详情，包括申请记录、报名记录、
     * 组织关系和积分记录，用于管理员详情弹窗或页面。
     */
    @GetMapping("/students/{id}")
    public ApiResponse<Object> studentDetail(@PathVariable Long id) {
        requireAdmin();
        Map<String, Object> student = db.one(studentSql("where u.user_id = ?"), id);
        student.put("leaderApply", db.maybeOne("""
                select la.*, reviewer.real_name reviewer_name
                from leader_apply la
                left join user_account reviewer on la.reviewer_id = reviewer.user_id
                where la.user_id = ?
                order by la.applied_at desc limit 1
                """, id));
        student.put("registrations", db.jdbc().queryForList("""
                select r.*, a.activity_name, a.start_time, a.activity_status, o.org_name
                from registration r
                join activity a on r.activity_id = a.activity_id
                join organization o on a.org_id = o.org_id
                where r.user_id = ?
                order by r.registered_at desc
                """, id));
        student.put("joinedOrgs", db.jdbc().queryForList("""
                select m.*, o.org_name, o.org_type
                from organization_member m
                join organization o on m.org_id = o.org_id
                where m.user_id = ?
                order by m.joined_at desc
                """, id));
        student.put("leadingOrgs", db.jdbc().queryForList("""
                select * from organization where principal_user_id = ? order by created_at desc
                """, id));
        student.put("scores", db.jdbc().queryForList("""
                select sr.*, a.activity_name, o.org_name
                from score_record sr
                join activity a on sr.activity_id = a.activity_id
                join organization o on a.org_id = o.org_id
                where sr.user_id = ?
                order by sr.submitted_at desc
                """, id));
        return ApiResponse.ok(student);
    }

    /**
     * 查询组织负责人申请列表，可按审核状态筛选。
     */
    @GetMapping("/leader-applies")
    public ApiResponse<Object> leaderApplies(@RequestParam(required = false) AuditStatus status) {
        requireAdmin();
        String condition = status == null ? "" : " where la.status = ?";
        Object[] args = status == null ? new Object[]{} : new Object[]{status.name()};
        return ApiResponse.ok(db.jdbc().queryForList("""
                select la.*, u.student_no, u.real_name, sp.college, sp.major, reviewer.real_name reviewer_name
                from leader_apply la
                join user_account u on la.user_id = u.user_id
                left join student_profile sp on u.user_id = sp.user_id
                left join user_account reviewer on la.reviewer_id = reviewer.user_id
                """ + condition + " order by la.applied_at desc", args));
    }

    /**
     * 通过或驳回学生的组织负责人申请。
     */
    @PatchMapping("/leader-applies/{id}")
    public ApiResponse<Object> auditLeader(@PathVariable Long id, @RequestBody AuditRequest request) {
        CurrentUser admin = requireAdmin();
        AuditStatus status = Db.require(request.status(), "status");
        ensureAuditDecision(status);
        Map<String, Object> apply = db.one("select * from leader_apply where apply_id=?", id);
        if (!AuditStatus.PENDING.name().equals(String.valueOf(apply.get("status")))) {
            throw new BusinessException("只有待审核负责人申请可以审批");
        }

        db.jdbc().update("""
                update leader_apply set status=?, reviewer_id=?, reject_reason=?, reviewed_at=now()
                where apply_id=?
                """, status.name(), admin.userId(), request.rejectReason(), id);

        // 审核通过后，在学生原有角色基础上额外授予组织负责人角色，
        // 使该用户下次登录后可以进入负责人相关页面。
        if (status == AuditStatus.APPROVED) {
            db.jdbc().update("""
                    insert ignore into user_role(user_id, role_id)
                    select ?, role_id from role where role_code='ORG_LEADER'
                    """, apply.get("user_id"));
        }
        messageService.notifyUser(
                ((Number) apply.get("user_id")).longValue(),
                status == AuditStatus.APPROVED ? "组织负责人申请已通过" : "组织负责人申请已驳回",
                status == AuditStatus.APPROVED
                        ? "你的组织负责人申请已通过，重新登录后可进入负责人首页。"
                        : "你的组织负责人申请未通过：" + (request.rejectReason() == null ? "未填写原因" : request.rejectReason()),
                MessageCategory.AUDIT,
                "LEADER_APPLY",
                id
        );
        return ApiResponse.ok(Map.of("applyId", id, "status", status.name()));
    }

    /**
     * 查询组织创建申请列表，可按审核状态筛选。
     */
    @GetMapping("/org-applies")
    public ApiResponse<Object> orgApplies(@RequestParam(required = false) AuditStatus status) {
        requireAdmin();
        String condition = status == null ? "" : " where oa.status = ?";
        Object[] args = status == null ? new Object[]{} : new Object[]{status.name()};
        return ApiResponse.ok(db.jdbc().queryForList("""
                select oa.*, u.student_no, u.real_name applicant_name, reviewer.real_name reviewer_name
                from organization_apply oa
                join user_account u on oa.applicant_id = u.user_id
                left join user_account reviewer on oa.reviewer_id = reviewer.user_id
                """ + condition + " order by oa.applied_at desc", args));
    }

    /**
     * 审核通过组织申请，并创建正式组织记录。
     */
    @PatchMapping("/org-applies/{id}")
    public ApiResponse<Object> auditOrg(@PathVariable Long id, @RequestBody AuditRequest request) {
        CurrentUser admin = requireAdmin();
        AuditStatus status = Db.require(request.status(), "status");
        ensureAuditDecision(status);
        Map<String, Object> apply = db.one("select * from organization_apply where org_apply_id=?", id);
        if (!AuditStatus.PENDING.name().equals(String.valueOf(apply.get("status")))) {
            throw new BusinessException("只有待审核组织申请可以审批");
        }

        db.jdbc().update("""
                update organization_apply set status=?, reviewer_id=?, reject_reason=?, reviewed_at=now()
                where org_apply_id=?
                """, status.name(), admin.userId(), request.rejectReason(), id);

        // 组织创建申请通过时，会立即创建组织，
        // 并将申请人设置为该组织的负责人成员。
        if (status == AuditStatus.APPROVED) {
            Long orgId = db.insert("""
                    insert into organization(org_name, org_type, description, contact, principal_user_id, org_status)
                    values(?, ?, ?, ?, ?, 'ACTIVE')
                    """, apply.get("org_name"), apply.get("org_type"), apply.get("description"),
                    apply.get("contact"), apply.get("applicant_id"));
            db.jdbc().update("""
                    insert ignore into organization_member(org_id, user_id, member_role, join_status)
                    values(?, ?, 'LEADER', 'APPROVED')
                    """, orgId, apply.get("applicant_id"));
        }
        messageService.notifyUser(
                ((Number) apply.get("applicant_id")).longValue(),
                status == AuditStatus.APPROVED ? "组织成立申请已通过" : "组织成立申请已驳回",
                status == AuditStatus.APPROVED
                        ? "你申请成立的组织“" + apply.get("org_name") + "”已通过审核。"
                        : "你申请成立的组织“" + apply.get("org_name") + "”未通过：" + (request.rejectReason() == null ? "未填写原因" : request.rejectReason()),
                MessageCategory.AUDIT,
                "ORGANIZATION_APPLY",
                id
        );
        return ApiResponse.ok(Map.of("orgApplyId", id, "status", status.name()));
    }

    /**
     * 查询管理员组织管理页面需要的全部组织。
     */
    @GetMapping("/organizations")
    public ApiResponse<Object> organizations() {
        requireAdmin();
        return ApiResponse.ok(db.jdbc().queryForList("""
                select o.*, u.real_name principal_name
                from organization o join user_account u on o.principal_user_id = u.user_id
                order by o.created_at desc
                """));
    }

    /**
     * 修改组织生命周期状态。停用组织时，同步作废该组织活动并取消有效报名，
     * 防止学生继续报名已停用组织下的活动。
     */
    @Transactional
    @PatchMapping("/organizations/{id}/status")
    public ApiResponse<Object> orgStatus(@PathVariable Long id, @RequestBody OrganizationStatusRequest request) {
        requireAdmin();
        OrganizationStatus status = Db.require(request.orgStatus(), "orgStatus");
        db.jdbc().update("update organization set org_status=? where org_id=?", status.name(), id);
        if (status == OrganizationStatus.DISABLED) {
            disableOrganizationActivities(id);
        }
        return ApiResponse.ok(Map.of("orgId", id, "orgStatus", status.name()));
    }

    /**
     * 停用组织后，统一把组织下活动置为下架，并取消这些活动的有效报名。
     * 这样即使前端缓存了旧活动信息，后端报名接口也不会继续接受报名。
     */
    private void disableOrganizationActivities(Long orgId) {
        db.jdbc().update("""
                update activity
                set activity_status='OFFLINE'
                where org_id=? and activity_status <> 'OFFLINE'
                """, orgId);
        db.jdbc().update("""
                update registration r
                join activity a on r.activity_id = a.activity_id
                set r.registration_status='CANCELLED'
                where a.org_id=? and r.registration_status='VALID'
                """, orgId);
        db.jdbc().update("""
                update activity a
                left join (
                    select activity_id, count(*) valid_count
                    from registration
                    where registration_status='VALID'
                    group by activity_id
                ) rc on a.activity_id = rc.activity_id
                set a.registered_count = coalesce(rc.valid_count, 0)
                where a.org_id=?
                """, orgId);
    }

    /**
     * 学生列表和详情查询共用的数据库查询片段。
     */
    private String studentSql(String where) {
        String whereClause = where == null || where.isBlank() ? "" : where.strip() + "\n";
        return """
                select u.user_id, u.student_no, u.username, u.real_name, u.phone, u.email, u.account_status,
                       sp.college, sp.major, sp.class_name className, sp.grade,
                       group_concat(r.role_code order by r.role_code separator ',') roles
                from user_account u
                left join student_profile sp on u.user_id = sp.user_id
                left join user_role ur on u.user_id = ur.user_id
                left join role r on ur.role_id = r.role_id
                """ + whereClause + """
                group by u.user_id, u.student_no, u.username, u.real_name, u.phone, u.email, u.account_status,
                         sp.college, sp.major, sp.class_name, sp.grade
                """;
    }

    /**
     * 防止调用方把“待审核”当作最终审核结果提交。
     */
    private void ensureAuditDecision(AuditStatus status) {
        if (status != AuditStatus.APPROVED && status != AuditStatus.REJECTED) {
            throw new BusinessException("审核状态只能是 APPROVED 或 REJECTED");
        }
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
