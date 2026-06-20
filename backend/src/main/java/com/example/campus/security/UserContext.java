package com.example.campus.security;

import com.example.campus.common.BusinessException;

/**
 * 保存当前请求认证用户的上下文工具。
 *
 * 认证拦截器会在控制器执行前设置该值，
 * 并在请求结束后清理。控制器因此可以直接获取
 * 当前用户，而不需要在每个方法参数中传递。
 */
public final class UserContext {
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    /**
     * 工具型封装类，不应被实例化。
     */
    private UserContext() {
    }

    /**
     * 保存当前请求线程的认证用户。
     */
    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /**
     * 读取认证用户；请求未认证时抛出业务异常。
     */
    public static CurrentUser get() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException("请先登录");
        }
        return user;
    }

    /**
     * 清理线程变量，避免一个请求的用户信息泄漏到
     * 之后同一服务器线程处理的其他请求中。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
