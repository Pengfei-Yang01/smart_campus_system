package com.example.campus.security;

import com.example.campus.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 通过读取认证令牌校验受保护的接口请求。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenService tokenService;

    /**
     * 注入用于校验认证请求头的令牌服务。
     */
    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * 在控制器方法执行前运行。它会放行跨域预检请求，
     * 校验普通接口调用的认证令牌，并保存当前用户。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new BusinessException("请先登录");
        }
        UserContext.set(tokenService.parse(auth.substring(7)));
        return true;
    }

    /**
     * 控制器执行结束后始终清理当前请求用户。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
