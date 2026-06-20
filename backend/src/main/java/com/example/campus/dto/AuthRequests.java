package com.example.campus.dto;

/**
 * 登录和注册请求数据对象。
 */
public final class AuthRequests {
    /**
     * 工具型封装类，不应被实例化。
     */
    private AuthRequests() {
    }

    /**
 * 登录请求体。登录名字段可以填写用户名或学号。
     *
     * @param username 用户名或学号
     * @param password 用户输入的明文密码，比较前会先计算摘要
     */
    public record LoginRequest(String username, String password) {
    }

    /**
     * 创建普通学生账号的注册请求体。
     *
     * @param username 唯一登录名
     * @param password 初始密码
     * @param studentNo 唯一学号
     * @param realName 学生真实姓名
     * @param phone 可选手机号
     * @param email 可选邮箱
     * @param college 可选学院名称
     * @param major 可选专业名称
     * @param className 可选班级名称
     * @param grade 可选年级文本
     */
    public record RegisterRequest(
            String username,
            String password,
            String studentNo,
            String realName,
            String phone,
            String email,
            String college,
            String major,
            String className,
            String grade
    ) {
    }
}
