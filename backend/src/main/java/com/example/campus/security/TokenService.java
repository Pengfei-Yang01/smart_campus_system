package com.example.campus.security;

import com.example.campus.common.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 用于无状态登录的轻量签名令牌服务。
 *
 * 令牌载荷使用结构化文本表示并通过安全编码处理，再用签名算法保护。
 * 该实现适合课程项目，同时可以防止前端篡改用户编号或角色数据。
 */
@Service
public class TokenService {
    private final ObjectMapper mapper;
    private final String secret;
    private final long tokenHours;

    /**
     * 使用对象转换器和配置文件中的令牌配置创建服务。
     */
    public TokenService(ObjectMapper mapper,
                        @Value("${app.token-secret}") String secret,
                        @Value("${app.token-hours}") long tokenHours) {
        this.mapper = mapper;
        this.secret = secret;
        this.tokenHours = tokenHours;
    }

    /**
     * 为认证用户生成已签名令牌。
     *
     * @param user 返回给前端的用户快照
     * @return 前端放入认证请求头的令牌字符串
     */
    public String create(CurrentUser user) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("uid", user.userId());
            payload.put("username", user.username());
            payload.put("realName", user.realName());
            payload.put("roles", user.roles());
            payload.put("exp", Instant.now().plusSeconds(tokenHours * 3600).getEpochSecond());

            String body = base64(mapper.writeValueAsBytes(payload));
            return body + "." + sign(body);
        } catch (Exception ex) {
            throw new BusinessException("生成登录令牌失败");
        }
    }

    /**
     * 校验令牌、检查过期时间，并还原当前用户。
     *
     * @param token 去掉认证前缀后的令牌
     * @return 认证后的用户快照
     */
    public CurrentUser parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) {
                throw new BusinessException("登录令牌无效");
            }

            byte[] json = Base64.getUrlDecoder().decode(parts[0]);
            Map<String, Object> payload = mapper.readValue(json, new TypeReference<>() {});
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                throw new BusinessException("登录已过期，请重新登录");
            }

            @SuppressWarnings("unchecked")
            Set<String> roles = new TreeSet<>((java.util.Collection<String>) payload.get("roles"));
            return new CurrentUser(
                    ((Number) payload.get("uid")).longValue(),
                    String.valueOf(payload.get("username")),
                    String.valueOf(payload.get("realName")),
                    roles
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("登录令牌解析失败");
        }
    }

    /**
     * 计算令牌载荷的安全签名。
     */
    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return base64(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 使用地址安全的编码方式处理令牌字节，并去掉填充字符。
     */
    private String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
