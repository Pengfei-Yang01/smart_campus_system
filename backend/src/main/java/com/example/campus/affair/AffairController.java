package com.example.campus.affair;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.AffairRequests.AffairApplicationRequest;
import com.example.campus.dto.AffairRequests.AffairAuditRequest;
import com.example.campus.enums.AffairApplicantScope;
import com.example.campus.enums.AffairApplicationStatus;
import com.example.campus.enums.MessageCategory;
import com.example.campus.enums.NoticeTargetRole;
import com.example.campus.message.MessageService;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.UserContext;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生事务申请接口。
 *
 * 学生可以申请桌椅、物资等基础事务；组织负责人在此基础上
 * 可以为自己负责的启用组织申请教室、场地等组织活动资源。
 */
@RestController
@RequestMapping("/api")
public class AffairController {
    private final Db db;
    private final MessageService messageService;

    /**
     * 注入数据库工具和消息服务。
     */
    public AffairController(Db db, MessageService messageService) {
        this.db = db;
        this.messageService = messageService;
    }

    /**
     * 查询当前用户可见的事务申请类型。
     */
    @GetMapping("/affairs/types")
    public ApiResponse<Object> types() {
        CurrentUser user = UserContext.get();
        if (user.isAdmin() || user.isLeader()) {
            return ApiResponse.ok(db.jdbc().queryForList("""
                    select * from affair_type
                    where enabled_status='ENABLED'
                    order by sort_order, type_id
                    """));
        }
        return ApiResponse.ok(db.jdbc().queryForList("""
                select * from affair_type
                where enabled_status='ENABLED' and applicant_scope='ALL'
                order by sort_order, type_id
                """));
    }

    /**
     * 查询事务申请可选择的校园资源。
     *
     * @param typeId 可选申请类型编号，用于过滤匹配资源
     */
    @GetMapping("/affairs/resources")
    public ApiResponse<Object> resources(@RequestParam(required = false) Long typeId) {
        if (typeId == null) {
            return ApiResponse.ok(db.jdbc().queryForList("""
                    select r.*, t.type_name
                    from campus_resource r
                    join affair_type t on r.type_id = t.type_id
                    where r.resource_status='ENABLED'
                    order by t.sort_order, r.resource_name
                    """));
        }
        return ApiResponse.ok(db.jdbc().queryForList("""
                select r.*, t.type_name
                from campus_resource r
                join affair_type t on r.type_id = t.type_id
                where r.resource_status='ENABLED' and r.type_id=?
                order by r.resource_name
                """, typeId));
    }

    /**
     * 查询当前用户提交过的事务申请。
     */
    @GetMapping("/affairs/applications/mine")
    public ApiResponse<Object> mine() {
        CurrentUser user = UserContext.get();
        return ApiResponse.ok(db.jdbc().queryForList(applicationSql("where aa.applicant_id=?"), user.userId()));
    }

    /**
     * 创建事务申请，并通知管理员处理。
     */
    @Transactional
    @PostMapping("/affairs/applications")
    public ApiResponse<Object> create(@RequestBody AffairApplicationRequest request) {
        CurrentUser user = UserContext.get();
        if (user.isAdmin()) {
            throw new BusinessException("管理员负责审批事务申请，不需要提交申请");
        }

        Map<String, Object> type = loadEnabledType(Db.require(request.typeId(), "typeId"));
        AffairApplicantScope scope = AffairApplicantScope.valueOf(String.valueOf(type.get("applicant_scope")));
        Long orgId = resolveApplicationOrg(scope, request.orgId());
        Long resourceId = resolveResource(request.resourceId(), request.typeId());
        LocalDateTime start = Db.dateTime(request.expectedStart(), "expectedStart");
        LocalDateTime end = Db.dateTime(request.expectedEnd(), "expectedEnd");
        validateTime(start, end);
        Integer quantity = request.quantity() == null ? 1 : request.quantity();
        if (quantity <= 0) {
            throw new BusinessException("申请数量必须大于 0");
        }

        Long id = db.insert("""
                insert into affair_application(applicant_id, applicant_role, org_id, type_id, resource_id,
                    title, apply_reason, expected_start, expected_end, quantity, contact)
                values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                user.userId(), user.isLeader() ? "ORG_LEADER" : "STUDENT", orgId, request.typeId(), resourceId,
                Db.require(request.title(), "title"), Db.require(request.applyReason(), "applyReason"),
                Db.ts(start), Db.ts(end), quantity, request.contact());
        messageService.notifyRole(
                NoticeTargetRole.ADMIN,
                "新的学生事务申请待审批",
                user.realName() + " 提交了事务申请：" + request.title(),
                MessageCategory.AFFAIR,
                "AFFAIR_APPLICATION",
                id
        );
        return ApiResponse.ok(Map.of("affairId", id));
    }

    /**
     * 当前申请人撤销尚未审批的事务申请。
     */
    @PatchMapping("/affairs/applications/{id}/cancel")
    public ApiResponse<Object> cancel(@PathVariable Long id) {
        CurrentUser user = UserContext.get();
        int updated = db.jdbc().update("""
                update affair_application
                set status='CANCELLED'
                where affair_id=? and applicant_id=? and status='PENDING'
                """, id, user.userId());
        if (updated == 0) {
            throw new BusinessException("只有待审核的本人申请可以撤销");
        }
        return ApiResponse.ok(Map.of("affairId", id, "status", AffairApplicationStatus.CANCELLED.name()));
    }

    /**
     * 管理员查询全部事务申请，可按状态筛选。
     */
    @GetMapping("/admin/affairs/applications")
    public ApiResponse<Object> adminApplications(@RequestParam(required = false) AffairApplicationStatus status) {
        requireAdmin();
        if (status == null) {
            return ApiResponse.ok(db.jdbc().queryForList(applicationSql(""), new Object[]{}));
        }
        return ApiResponse.ok(db.jdbc().queryForList(applicationSql("where aa.status=?"), status.name()));
    }

    /**
     * 管理员审批事务申请。
     *
     * 审批通过带有明确资源的申请时，会检查同一资源是否已经存在
     * 时间重叠的通过申请，避免教室、场地等资源被重复占用。
     */
    @Transactional
    @PatchMapping("/admin/affairs/applications/{id}/audit")
    public ApiResponse<Object> audit(@PathVariable Long id, @RequestBody AffairAuditRequest request) {
        CurrentUser admin = requireAdmin();
        AffairApplicationStatus status = Db.require(request.status(), "status");
        if (status != AffairApplicationStatus.APPROVED && status != AffairApplicationStatus.REJECTED) {
            throw new BusinessException("事务申请只能审批为 APPROVED 或 REJECTED");
        }
        Map<String, Object> apply = db.one("select * from affair_application where affair_id=?", id);
        if (!AffairApplicationStatus.PENDING.name().equals(String.valueOf(apply.get("status")))) {
            throw new BusinessException("只有待审核申请可以审批");
        }
        if (status == AffairApplicationStatus.APPROVED) {
            ensureResourceAvailable(apply);
        }

        db.jdbc().update("""
                update affair_application
                set status=?, reviewer_id=?, reject_reason=?, review_remark=?, reviewed_at=now()
                where affair_id=?
                """, status.name(), admin.userId(), request.rejectReason(), request.reviewRemark(), id);
        Long applicantId = ((Number) apply.get("applicant_id")).longValue();
        messageService.notifyUser(
                applicantId,
                status == AffairApplicationStatus.APPROVED ? "事务申请已通过" : "事务申请已驳回",
                status == AffairApplicationStatus.APPROVED
                        ? "你的事务申请“" + apply.get("title") + "”已通过，请按审批说明执行。"
                        : "你的事务申请“" + apply.get("title") + "”未通过：" + (request.rejectReason() == null ? "未填写原因" : request.rejectReason()),
                MessageCategory.AUDIT,
                "AFFAIR_APPLICATION",
                id
        );
        return ApiResponse.ok(Map.of("affairId", id, "status", status.name()));
    }

    /**
     * 加载启用中的事务类型。
     */
    private Map<String, Object> loadEnabledType(Long typeId) {
        return db.one("""
                select * from affair_type
                where type_id=? and enabled_status='ENABLED'
                """, typeId);
    }

    /**
     * 根据事务类型和当前角色确定申请关联组织。
     */
    private Long resolveApplicationOrg(AffairApplicantScope scope, Long requestedOrgId) {
        CurrentUser user = UserContext.get();
        if (scope == AffairApplicantScope.ORG_LEADER) {
            if (!user.isLeader()) {
                throw new BusinessException("该事务类型只有组织负责人可以申请");
            }
            Long orgId = Db.require(requestedOrgId, "orgId");
            ensureManagedActiveOrg(orgId);
            return orgId;
        }
        if (requestedOrgId != null) {
            ensureManagedActiveOrg(requestedOrgId);
        }
        return requestedOrgId;
    }

    /**
     * 校验资源是否启用且匹配申请类型。
     */
    private Long resolveResource(Long resourceId, Long typeId) {
        if (resourceId == null) {
            return null;
        }
        if (db.count("""
                select count(*) from campus_resource
                where resource_id=? and type_id=? and resource_status='ENABLED'
                """, resourceId, typeId) == 0) {
            throw new BusinessException("选择的资源不可用或不匹配申请类型");
        }
        return resourceId;
    }

    /**
     * 校验负责人只能代表自己负责的启用组织申请资源。
     */
    private void ensureManagedActiveOrg(Long orgId) {
        CurrentUser user = UserContext.get();
        if (!user.isLeader()) {
            throw new BusinessException("只有组织负责人可以代表组织提交申请");
        }
        if (db.count("""
                select count(*) from organization
                where org_id=? and principal_user_id=? and org_status='ACTIVE'
                """, orgId, user.userId()) == 0) {
            throw new BusinessException("只能为自己负责的启用组织提交申请");
        }
    }

    /**
     * 校验申请起止时间。
     */
    private void validateTime(LocalDateTime start, LocalDateTime end) {
        if (end.isBefore(start) || end.isEqual(start)) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
    }

    /**
     * 审批通过前检查资源时间是否冲突。
     */
    private void ensureResourceAvailable(Map<String, Object> apply) {
        Object resourceId = apply.get("resource_id");
        if (resourceId == null) {
            return;
        }
        LocalDateTime start = Db.localDateTime(apply.get("expected_start"));
        LocalDateTime end = Db.localDateTime(apply.get("expected_end"));
        int conflict = db.count("""
                select count(*) from affair_application
                where resource_id=? and status='APPROVED' and affair_id<>?
                  and expected_start < ? and expected_end > ?
                """, resourceId, apply.get("affair_id"), Db.ts(end), Db.ts(start));
        if (conflict > 0) {
            throw new BusinessException("该资源在申请时间段内已被占用");
        }
    }

    /**
     * 申请列表查询共用 SQL。
     */
    private String applicationSql(String where) {
        String whereClause = where == null || where.isBlank() ? "" : where.strip() + "\n";
        return """
                select aa.*, t.type_code, t.type_name, t.applicant_scope,
                       cr.resource_name, cr.resource_location,
                       o.org_name, applicant.real_name applicant_name,
                       applicant.student_no, reviewer.real_name reviewer_name
                from affair_application aa
                join affair_type t on aa.type_id = t.type_id
                left join campus_resource cr on aa.resource_id = cr.resource_id
                left join organization o on aa.org_id = o.org_id
                join user_account applicant on aa.applicant_id = applicant.user_id
                left join user_account reviewer on aa.reviewer_id = reviewer.user_id
                """ + whereClause + """
                order by aa.applied_at desc, aa.affair_id desc
                """;
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
