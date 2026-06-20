package com.example.campus.dto;

import com.example.campus.enums.AuditStatus;
import com.example.campus.enums.OrganizationStatus;

/**
 * 管理员审核操作请求数据对象。
 */
public final class AdminRequests {
    /**
     * 工具型封装类，不应被实例化。
     */
    private AdminRequests() {
    }

    /**
     * 负责人申请、组织申请等管理员审核动作共用的请求体。
     * 
     *
 * @param status 审核结果；控制器只接受通过或驳回
     * @param rejectReason 审核驳回时展示给申请人的原因
     */
    public record AuditRequest(AuditStatus status, String rejectReason) {
    }

    /**
     * 管理员启用或停用组织时使用的请求体。
     *
     * @param orgStatus 目标组织生命周期状态
     */
    public record OrganizationStatusRequest(OrganizationStatus orgStatus) {
    }
}
