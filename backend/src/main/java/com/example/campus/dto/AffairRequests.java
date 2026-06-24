package com.example.campus.dto;

import com.example.campus.enums.AffairApplicationStatus;

/**
 * 学生事务申请模块的请求数据对象。
 */
public final class AffairRequests {
    /**
     * 工具型封装类，不应被实例化。
     */
    private AffairRequests() {
    }

    /**
     * 创建学生事务申请的请求体。
     *
     * @param typeId 申请类型编号，对应 affair_type.type_id
     * @param resourceId 可选资源编号，对应 campus_resource.resource_id
     * @param orgId 组织负责人代组织申请资源时填写的组织编号
     * @param title 申请标题，用于列表快速识别
     * @param applyReason 申请理由，提交给管理员审核
     * @param expectedStart 期望开始使用时间
     * @param expectedEnd 期望结束使用时间
     * @param quantity 申请数量，例如桌椅数量
     * @param contact 联系方式
     */
    public record AffairApplicationRequest(
            Long typeId,
            Long resourceId,
            Long orgId,
            String title,
            String applyReason,
            String expectedStart,
            String expectedEnd,
            Integer quantity,
            String contact
    ) {
    }

    /**
     * 管理员审批事务申请的请求体。
     *
     * @param status 审批结果，只允许 APPROVED 或 REJECTED
     * @param rejectReason 驳回原因
     * @param reviewRemark 审批说明，例如领取地点或使用注意事项
     */
    public record AffairAuditRequest(
            AffairApplicationStatus status,
            String rejectReason,
            String reviewRemark
    ) {
    }
}
