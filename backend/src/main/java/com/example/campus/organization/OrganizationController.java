package com.example.campus.organization;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.OrganizationRequests.JoinRequest;
import com.example.campus.dto.OrganizationRequests.MemberAuditRequest;
import com.example.campus.dto.OrganizationRequests.OrganizationApplyRequest;
import com.example.campus.dto.OrganizationRequests.OrganizationUpdateRequest;
import com.example.campus.enums.JoinStatus;
import com.example.campus.enums.MessageCategory;
import com.example.campus.enums.NoticeTargetRole;
import com.example.campus.message.MessageService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织浏览、申请和成员管理接口。
 */
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final Db db;
    private final MessageService messageService;

    /**
     * 注入组织模块使用的数据库工具类和消息服务。
     */
    public OrganizationController(Db db, MessageService messageService) {
        this.db = db;
        this.messageService = messageService;
    }

    /**
     * 查询组织列表，并包含当前用户与组织的关系状态。
     */
    @GetMapping
    public ApiResponse<Object> list() {
        CurrentUser user = UserContext.get();
        return ApiResponse.ok(db.jdbc().queryForList("""
                select o.*, u.real_name principal_name,
                       (select join_status from organization_member m
                        where m.org_id=o.org_id and m.user_id=?
                        order by joined_at desc limit 1) my_status
                from organization o
                join user_account u on o.principal_user_id = u.user_id
                order by o.created_at desc
                """, user.userId()));
    }

    /**
     * 返回组织详情以及该组织发布的活动。
     */
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

    /**
     * 组织负责人可以申请创建新组织。
     */
    @PostMapping("/apply")
    public ApiResponse<Object> applyCreate(@RequestBody OrganizationApplyRequest request) {
        CurrentUser user = UserContext.get();
        if (!user.isLeader()) {
            throw new BusinessException("只有组织负责人可以申请创建组织");
        }
        if (db.count("select count(*) from organization_apply where applicant_id=? and status='PENDING'", user.userId()) > 0) {
            throw new BusinessException("你已有待审核的组织申请");
        }

        Long id = db.insert("""
                insert into organization_apply(applicant_id, org_name, org_type, description, apply_reason, contact)
                values(?, ?, ?, ?, ?, ?)
                """, user.userId(), Db.require(request.orgName(), "orgName"), Db.require(request.orgType(), "orgType").name(),
                request.description(), Db.require(request.applyReason(), "applyReason"), request.contact());
        messageService.notifyRole(
                NoticeTargetRole.ADMIN,
                "新的组织成立申请待审批",
                user.realName() + " 申请成立组织：" + request.orgName(),
                MessageCategory.AUDIT,
                "ORGANIZATION_APPLY",
                id
        );
        return ApiResponse.ok(Map.of("orgApplyId", id));
    }

    /**
     * 查询当前用户提交的组织申请。
     */
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

    /**
     * 更新组织基本信息。
     */
    @PutMapping("/{id}")
    public ApiResponse<Object> update(@PathVariable Long id, @RequestBody OrganizationUpdateRequest request) {
        ensureCanManageOrg(id);
        Map<String, Object> org = db.one("select org_status from organization where org_id=?", id);
        if (!"ACTIVE".equals(String.valueOf(org.get("org_status")))) {
            throw new BusinessException("当前组织已被停用，请联系管理员");
        }
        db.jdbc().update("""
                update organization set org_name=?, org_type=?, description=?, contact=?
                where org_id=?
                """, Db.require(request.orgName(), "orgName"), Db.require(request.orgType(), "orgType").name(),
                request.description(), request.contact(), id);
        return ApiResponse.ok(Map.of("orgId", id));
    }

    /**
     * 允许学生申请加入启用状态的组织。
     */
    @PostMapping("/{id}/join")
    public ApiResponse<Object> join(@PathVariable Long id, @RequestBody JoinRequest request) {
        CurrentUser user = UserContext.get();
        Map<String, Object> org = db.one("select * from organization where org_id=?", id);
        if (!"ACTIVE".equals(String.valueOf(org.get("org_status")))) {
            throw new BusinessException("组织未启用，不能申请加入");
        }

        Map<String, Object> old = db.maybeOne("select * from organization_member where org_id=? and user_id=?", id, user.userId());
        if (old != null && JoinStatus.APPROVED.name().equals(String.valueOf(old.get("join_status")))) {
            throw new BusinessException("你已加入该组织");
        }
        if (old != null && JoinStatus.PENDING.name().equals(String.valueOf(old.get("join_status")))) {
            throw new BusinessException("你的加入申请正在审核中");
        }

        // 被驳回或已退出的成员可以再次申请；这里复用已有记录，
        // 因为组织成员表以组织编号和用户编号作为主键。
        if (old == null) {
            db.jdbc().update("""
                    insert into organization_member(org_id, user_id, member_role, join_status, apply_reason)
                    values(?, ?, 'MEMBER', 'PENDING', ?)
                    """, id, user.userId(), request.applyReason());
        } else {
            db.jdbc().update("""
                    update organization_member set join_status='PENDING', apply_reason=?, reject_reason=null, joined_at=now()
                    where org_id=? and user_id=?
                    """, request.applyReason(), id, user.userId());
        }
        messageService.notifyUser(
                ((Number) org.get("principal_user_id")).longValue(),
                "新的组织成员申请",
                user.realName() + " 申请加入组织“" + org.get("org_name") + "”。",
                MessageCategory.AUDIT,
                "ORGANIZATION_MEMBER",
                id
        );
        return ApiResponse.ok(Map.of("orgId", id));
    }

    /**
     * 查询组织成员和待审核的加入申请。
     */
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

    /**
     * 通过或驳回待审核的组织加入申请。
     */
    @PatchMapping("/{id}/members/{userId}")
    public ApiResponse<Object> auditMember(@PathVariable Long id,
                                           @PathVariable Long userId,
                                           @RequestBody MemberAuditRequest request) {
        ensureCanManageOrg(id);
        JoinStatus status = Db.require(request.joinStatus(), "joinStatus");
        if (status != JoinStatus.APPROVED && status != JoinStatus.REJECTED) {
            throw new BusinessException("成员审核状态只能是 APPROVED 或 REJECTED");
        }

        int updated = db.jdbc().update("""
                update organization_member set join_status=?, reject_reason=?
                where org_id=? and user_id=? and join_status='PENDING'
                """, status.name(), request.rejectReason(), id, userId);
        if (updated == 0) {
            throw new BusinessException("待审核成员申请不存在");
        }
        Map<String, Object> org = db.one("select org_name from organization where org_id=?", id);
        messageService.notifyUser(
                userId,
                status == JoinStatus.APPROVED ? "组织加入申请已通过" : "组织加入申请已驳回",
                status == JoinStatus.APPROVED
                        ? "你加入组织“" + org.get("org_name") + "”的申请已通过。"
                        : "你加入组织“" + org.get("org_name") + "”的申请未通过：" + (request.rejectReason() == null ? "未填写原因" : request.rejectReason()),
                MessageCategory.AUDIT,
                "ORGANIZATION_MEMBER",
                id
        );
        return ApiResponse.ok(Map.of("orgId", id, "userId", userId));
    }

    /**
     * 校验当前用户是否可以管理目标组织。
     */
    private void ensureCanManageOrg(Long orgId) {
        CurrentUser user = UserContext.get();
        if (user.isAdmin()) {
            return;
        }
        if (!user.isLeader()
                || db.count("select count(*) from organization where org_id=? and principal_user_id=?", orgId, user.userId()) == 0) {
            throw new BusinessException("你只能管理自己负责的组织");
        }
    }
}
