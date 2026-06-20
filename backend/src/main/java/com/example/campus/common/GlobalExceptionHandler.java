package com.example.campus.common;

import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 把异常转换成前端统一处理的接口响应格式。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 处理可预期的业务规则错误，例如权限不足或
     * 重复报名，并返回客户端可读的 400 响应。
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, Object>> business(BusinessException ex) {
        return ApiResponse.fail(ex.getMessage());
    }

    /**
     * 把唯一键冲突转换成可读的重复数据提示。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, Object>> duplicate() {
        return ApiResponse.fail("数据已存在，请勿重复提交");
    }

    /**
     * 处理使用校验注解时产生的参数校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .orElse("参数校验失败");
        return ApiResponse.fail(message);
    }

    /**
     * 处理请求体格式错误、枚举值错误和查询参数类型错误。
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, Object>> badRequest() {
        return ApiResponse.fail("请求参数格式不正确");
    }

    /**
     * 处理兜底异常，保持响应格式统一，
     * 避免前端收到原始网页错误页。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Map<String, Object>> general(Exception ex) {
        return ApiResponse.fail(ex.getMessage());
    }
}
