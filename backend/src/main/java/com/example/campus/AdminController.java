package com.example.campus;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final Db db;

    public AdminController(Db db) {
        this.db = db;
    }

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
                "pendingScoreCount", db.count("select count(*) from score_record where audit_status='PENDING'")
        ));
    }

    @GetMapping("/students")
    public ApiResponse<Object> students(@RequestParam(required = false) String keyword) {
        requireAdmin();
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.ok(db.jdbc().queryForList(studentSql("") + " order by u.user_id"));
        }
        String kw = "%" + keyword + "%";
        return ApiResponse.ok(db.jdbc().queryForList(
                studentSql("where u.username like ? or u.student_no like ? or u.real_name like ?") + " order by u.user_id",
                kw, kw, kw));
    }

    @PatchMapping("/students/{id}")
    public ApiResponse<Object> updateStudent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireAdmin();
        db.jdbc().update("""
                update user_account set real_name=?, phone=?, email=?, account_status=?
                where user_id=?
                """, Db.required(body, "realName"), Db.str(body, "phone"), Db.str(body, "email"),
                Db.required(body, "accountStatus"), id);
        db.jdbc().update("""
                update student_profile set college=?, major=?, class_name=?, grade=?
                where user_id=?
                """, Db.str(body, "college"), Db.str(body, "major"), Db.str(body, "className"), Db.str(body, "grade"), id);
        return ApiResponse.ok(Map.of("userId", id));
    }

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

    @GetMapping("/leader-applies")
    public ApiResponse<Object> leaderApplies(@RequestParam(required = false) String status) {
        requireAdmin();
        String condition = status == null || status.isBlank() ? "" : " where la.status = ?";
        Object[] args = condition.isBlank() ? new Object[]{} : new Object[]{status};
        return ApiResponse.ok(db.jdbc().queryForList("""
                select la.*, u.student_no, u.real_name, sp.college, sp.major, reviewer.real_name reviewer_name
                from leader_apply la
                join user_account u on la.user_id = u.user_id
                left join student_profile sp on u.user_id = sp.user_id
                left join user_account reviewer on la.reviewer_id = reviewer.user_id
                """ + condition + " order by la.applied_at desc", args));
    }

    @PatchMapping("/leader-applies/{id}")
    public ApiResponse<Object> auditLeader(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CurrentUser admin = requireAdmin();
        String status = Db.required(body, "status");
        if (!status.equals("APPROVED") && !status.equals("REJECTED")) {
            throw new BusinessException("审批状态只能为 APPROVED 或 REJECTED");
        }
        Map<String, Object> apply = db.one("select * from leader_apply where apply_id=?", id);
        db.jdbc().update("""
                update leader_apply set status=?, reviewer_id=?, reject_reason=?, reviewed_at=now()
                where apply_id=?
                """, status, admin.userId(), Db.str(body, "rejectReason"), id);
        if ("APPROVED".equals(status)) {
            db.jdbc().update("""
                    insert ignore into user_role(user_id, role_id)
                    select ?, role_id from role where role_code='ORG_LEADER'
                    """, apply.get("user_id"));
        }
        return ApiResponse.ok(Map.of("applyId", id, "status", status));
    }

    @GetMapping("/org-applies")
    public ApiResponse<Object> orgApplies(@RequestParam(required = false) String status) {
        requireAdmin();
        String condition = status == null || status.isBlank() ? "" : " where oa.status = ?";
        Object[] args = condition.isBlank() ? new Object[]{} : new Object[]{status};
        return ApiResponse.ok(db.jdbc().queryForList("""
                select oa.*, u.student_no, u.real_name applicant_name, reviewer.real_name reviewer_name
                from organization_apply oa
                join user_account u on oa.applicant_id = u.user_id
                left join user_account reviewer on oa.reviewer_id = reviewer.user_id
                """ + condition + " order by oa.applied_at desc", args));
    }

    @PatchMapping("/org-applies/{id}")
    public ApiResponse<Object> auditOrg(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CurrentUser admin = requireAdmin();
        String status = Db.required(body, "status");
        if (!status.equals("APPROVED") && !status.equals("REJECTED")) {
            throw new BusinessException("审批状态只能为 APPROVED 或 REJECTED");
        }
        Map<String, Object> apply = db.one("select * from organization_apply where org_apply_id=?", id);
        db.jdbc().update("""
                update organization_apply set status=?, reviewer_id=?, reject_reason=?, reviewed_at=now()
                where org_apply_id=?
                """, status, admin.userId(), Db.str(body, "rejectReason"), id);
        if ("APPROVED".equals(status)) {
            Long orgId = db.insert("""
                    insert into organization(org_name, org_type, description, contact, principal_user_id, org_status)
                    values(?, ?, ?, ?, ?, 'ACTIVE')
                    """, apply.get("org_name"), apply.get("org_type"), apply.get("description"), apply.get("contact"), apply.get("applicant_id"));
            db.jdbc().update("""
                    insert ignore into organization_member(org_id, user_id, member_role, join_status)
                    values(?, ?, 'LEADER', 'APPROVED')
                    """, orgId, apply.get("applicant_id"));
        }
        return ApiResponse.ok(Map.of("orgApplyId", id, "status", status));
    }

    @GetMapping("/organizations")
    public ApiResponse<Object> organizations() {
        requireAdmin();
        return ApiResponse.ok(db.jdbc().queryForList("""
                select o.*, u.real_name principal_name
                from organization o join user_account u on o.principal_user_id = u.user_id
                order by o.created_at desc
                """));
    }

    @PatchMapping("/organizations/{id}/status")
    public ApiResponse<Object> orgStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireAdmin();
        db.jdbc().update("update organization set org_status=? where org_id=?", Db.required(body, "orgStatus"), id);
        return ApiResponse.ok(Map.of("orgId", id));
    }

    private String studentSql(String where) {
        return """
                select u.user_id, u.student_no, u.username, u.real_name, u.phone, u.email, u.account_status,
                       sp.college, sp.major, sp.class_name className, sp.grade,
                       group_concat(r.role_code order by r.role_code separator ',') roles
                from user_account u
                left join student_profile sp on u.user_id = sp.user_id
                left join user_role ur on u.user_id = ur.user_id
                left join role r on ur.role_id = r.role_id
                """ + where + """
                group by u.user_id, u.student_no, u.username, u.real_name, u.phone, u.email, u.account_status,
                         sp.college, sp.major, sp.class_name, sp.grade
                """;
    }

    private CurrentUser requireAdmin() {
        CurrentUser user = UserContext.get();
        if (!user.isAdmin()) {
            throw new BusinessException("需要管理员权限");
        }
        return user;
    }
}
