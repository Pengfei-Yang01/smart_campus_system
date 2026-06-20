package com.example.campus.common;

/**
 * 所有后端接口统一返回的响应包装。
 *
 * @param code 0 表示成功，非 0 表示业务或系统失败
 * @param message 给用户或前端展示的响应消息
 * @param data 响应数据内容
 */
public record ApiResponse<T>(int code, String message, T data) {
    /**
     * 使用统一成功状态码构造成功响应。
     *
     * @param data 返回给前端的数据内容
     * @return 由后端框架序列化后的响应对象
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    /**
     * 为可预期的业务错误或校验错误构造失败响应。
     *
     * @param message 前端展示的错误消息
     * @return 带有非 0 状态码的响应对象
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(400, message, null);
    }
}
