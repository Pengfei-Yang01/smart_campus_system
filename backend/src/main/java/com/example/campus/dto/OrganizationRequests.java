package com.example.campus.dto;

import com.example.campus.enums.JoinStatus;
import com.example.campus.enums.OrgType;

/**
 * 组织申请和成员审核请求数据对象。
 */
public final class OrganizationRequests {
    /**
     * 工具型封装类，不应被实例化。
     */
    private OrganizationRequests() {
    }

    /**
     * 组织负责人申请创建新组织的请求体。
     *
     * @param orgName 拟创建的组织名称
     * @param orgType 数据库枚举中保存的组织类型
     * @param description 展示给学生的组织简介
     * @param applyReason 提交给管理员审核的申请理由
     * @param contact 组织联系方式
     */
    public record OrganizationApplyRequest(
            String orgName,
            OrgType orgType,
            String description,
            String applyReason,
            String contact
    ) {
    }

    /**
     * 编辑已有组织的请求体。
     *
     * @param orgName 更新后的组织名称
     * @param orgType 更新后的组织类型
     * @param description 更新后的简介
     * @param contact 更新后的联系方式
     */
    public record OrganizationUpdateRequest(
            String orgName,
            OrgType orgType,
            String description,
            String contact
    ) {
    }

    /**
     * 学生申请加入组织的请求体。
     *
     * @param applyReason 学生加入组织的理由
     */
    public record JoinRequest(String applyReason) {
    }

    /**
     * 审核成员加入申请的请求体。
     *
 * @param joinStatus 目标成员状态；此处只接受通过或驳回
     * @param rejectReason 可选驳回原因
     */
    public record MemberAuditRequest(JoinStatus joinStatus, String rejectReason) {
    }
}
