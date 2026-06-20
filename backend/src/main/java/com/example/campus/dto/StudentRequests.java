package com.example.campus.dto;

import com.example.campus.enums.AccountStatus;

/**
 * 学生自助操作和管理员编辑请求数据对象。
 */
public final class StudentRequests {
    /**
     * 工具型封装类，不应被实例化。
     */
    private StudentRequests() {
    }

    /**
     * 学生申请成为组织负责人的请求体。
     *
     * @param applyReason 申请理由
     * @param contact 管理员联系申请人时使用的联系方式
     * @param experience 相关组织或活动经历
     */
    public record LeaderApplyRequest(String applyReason, String contact, String experience) {
    }

    /**
     * 管理员编辑学生账号和资料的请求体。
     *
     * @param realName 学生真实姓名
     * @param phone 手机号
     * @param email 邮箱
 * @param accountStatus 账号启用状态
     * @param college 学院名称
     * @param major 专业名称
     * @param className 班级名称
     * @param grade 年级文本
     */
    public record StudentUpdateRequest(
            String realName,
            String phone,
            String email,
            AccountStatus accountStatus,
            String college,
            String major,
            String className,
            String grade
    ) {
    }
}
