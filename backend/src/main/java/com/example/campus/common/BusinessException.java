package com.example.campus.common;

/**
 * 用于表示可预期业务规则异常的异常类。
 *
 * 典型场景包括重复报名、非法活动状态流转、权限不足和参数校验不通过。
 */
public class BusinessException extends RuntimeException {
    /**
     * 创建一个应该以可读业务错误形式返回给客户端的异常，
     * 而不是作为内部服务器错误处理。
     *
     * @param message 面向用户的错误消息
     */
    public BusinessException(String message) {
        super(message);
    }
}
