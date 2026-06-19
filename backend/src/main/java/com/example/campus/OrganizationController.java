package com.example.campus;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final Db db;

    public OrganizationController(Db db) {
        this.db = db;
    }

    @GetMapping
    public ApiResponse<Object> list() {
        CurrentUser user = UserContext.get();
        return ApiResponse.ok(db.jdbc().queryForList("""
                select o.*, u.real_name principal_name,
                       (select join_status from organization_member m where m.org_id=o.org_id and m.user_id=? order by joined_at desc limit 1) my_status
                from organization o
                join user_account u on o.principal_user_id = u.user_id
                order by o.created_at desc
                """, user.userId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Object> detail(@PathVariable Long id) {
        Map<String, Object> org = db.one("""
                select o.*, u.real_name principal_name
                from organization o join user_account u on o.principal_user_id = u.user_id
                where o.org_id = ?
                """, id);
        org.put("activities", db.jdbc().queryForList("""
                select a.*, t.type_name
                from activity a join activity_type t on a.type_id=t.type_id
                where a.org_id=?
                order by a.start_time desc
                """, id));
        return ApiResponse.ok(org);
    }

    @PostMapping("/apply")
    public ApiResponse<Object> applyCreate(@RequestBody Map<String, Object> body) {
        CurrentUser user = UserContext.get();
        if (!user.isLeader()) {
            throw new BusinessException("只有组织负责人可以提交组织成立申请");
        }
        if (db.count("select count(*) from organization_apply where applicant_id=? and status='PENDING'", user.userId()) > 0) {
            throw new BusinessException("已有待审核的组织成立申请");
        }
        Long id = db.insert("""
                insert into organization_apply(applicant_id, org_name, org_type, description, apply_reason, contact)
                values(?, ?, ?, ?, ?, ?)
                """, user.userId(), Db.required(body, "orgName"), Db.required(body, "orgType"),
                Db.str(body, "description"), Db.required(body, "applyReason"), Db.str(body, "contact"));
        return ApiResponse.ok(Map.of("orgApplyId", id));
    }

    @GetMapping("/applies/mine")
    public ApiResponse<Object> myApplies() {
        CurrentUser user = UserContext.get();
        return ApiResponse.ok(db.jdbc().queryForList("""
                select oa.*, reviewer.real_name reviewer_name
                from organization_apply oa
                left join user_account reviewer on oa.reviewer_id = reviewer.user_id
                where oa.applicant_id = ?
                order by oa.applied_at desc
                """, user.userId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ensureCanManageOrg(id);
        db.jdbc().update("""
                update organization set org_name=?, org_type=?, description=?, contact=?
                where org_id=?
                """, Db.required(body, "orgName"), Db.required(body, "orgType"),
                Db.str(body, "description"), Db.str(body, "contact"), id);
        return ApiResponse.ok(Map.of("orgId", id));
    }

    @PostMapping("/{id}/join")
    public ApiResponse<Object> join(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CurrentUser user = UserContext.get();
        Map<String, Object> org = db.one("select * from organization where org_id=?", id);
        if (!"ACTIVE".equals(String.valueOf(org.get("org_status")))) {
            throw new BusinessException("组织未启用，暂不能申请加入");
        }
        Map<String, Object> old = db.maybeOne("select * from organization_member where org_id=? and user_id=?", id, user.userId());
        if (old != null && "APPROVED".equals(String.valueOf(old.get("join_status")))) {
            throw new BusinessException("你已经是该组织成员");
        }
        if (old != null && "PENDING".equals(String.valueOf(old.get("join_status")))) {
            throw new BusinessException("已有待审批的加入申请");
        }
        if (old == null) {
            db.jdbc().update("""
                    insert into organization_member(org_id, user_id, member_role, join_status, apply_reason)
                    values(?, ?, 'MEMBER', 'PENDING', ?)
                    """, id, user.userId(), Db.str(body, "applyReason"));
        } else {
            db.jdbc().update("""
                    update organization_member set join_status='PENDING', apply_reason=?, reject_reason=null, joined_at=now()
                    where org_id=? and user_id=?
                    """, Db.str(body, "applyReason"), id, user.userId());
        }
        return ApiResponse.ok(Map.of("orgId", id));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<Object> members(@PathVariable Long id) {
        ensureCanManageOrg(id);
        return ApiResponse.ok(db.jdbc().queryForList("""
                select m.*, u.student_no, u.real_name, sp.college, sp.major, sp.class_name
                from organization_member m
                join user_account u on m.user_id = u.user_id
                left join student_profile sp on u.user_id = sp.user_id
                where m.org_id = ?
                order by m.join_status, m.joined_at desc
                """, id));
    }

    @PatchMapping("/{id}/members/{userId}")
    public ApiResponse<Object> auditMember(@PathVariable Long id, @PathVariable Long userId, @RequestBody Map<String, Object> body) {
        ensureCanManageOrg(id);
        String status = Db.required(body, "joinStatus");
        if (!status.equals("APPROVED") && !status.equals("REJECTED")) {
            throw new BusinessException("成员审批状态只能为 APPROVED 或 REJECTED");
        }
        db.jdbc().update("""
                update organization_member set join_status=?, reject_reason=?
                where org_id=? and user_id=? and join_status='PENDING'
                """, status, Db.str(body, "rejectReason"), id, userId);
        return ApiResponse.ok(Map.of("orgId", id, "userId", userId));
    }

    private void ensureCanManageOrg(Long orgId) {
        CurrentUser user = UserContext.get();
        if (user.isAdmin()) {
            return;
        }
        if (!user.isLeader() || db.count("select count(*) from organization where org_id=? and principal_user_id=?", orgId, user.userId()) == 0) {
            throw new BusinessException("只能管理自己负责的组织");
        }
    }
}
