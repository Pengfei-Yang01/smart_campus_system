package com.example.campus;

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

@Service
public class TokenService {
    private final ObjectMapper mapper;
    private final String secret;
    private final long tokenHours;

    public TokenService(ObjectMapper mapper,
                        @Value("${app.token-secret}") String secret,
                        @Value("${app.token-hours}") long tokenHours) {
        this.mapper = mapper;
        this.secret = secret;
        this.tokenHours = tokenHours;
    }

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

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return base64(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
