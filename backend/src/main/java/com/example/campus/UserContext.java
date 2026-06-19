package com.example.campus;

public final class UserContext {
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException("请先登录");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
