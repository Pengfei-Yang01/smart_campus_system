package com.example.campus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final Db db;
    private final TokenService tokenService;

    public AuthController(Db db, TokenService tokenService) {
        this.db = db;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        String username = Db.required(body, "username");
        String password = Db.required(body, "password");
        String studentNo = Db.required(body, "studentNo");
        String realName = Db.required(body, "realName");
        Long userId = db.insert("""
                insert into user_account(student_no, username, password_hash, real_name, phone, email)
                values(?, ?, ?, ?, ?, ?)
                """, studentNo, username, Db.sha256(password), realName, Db.str(body, "phone"), Db.str(body, "email"));
        db.jdbc().update("""
                insert into student_profile(user_id, college, major, class_name, grade)
                values(?, ?, ?, ?, ?)
                """, userId, Db.str(body, "college"), Db.str(body, "major"), Db.str(body, "className"), Db.str(body, "grade"));
        db.jdbc().update("""
                insert into user_role(user_id, role_id)
                select ?, role_id from role where role_code = 'STUDENT'
                """, userId);
        return ApiResponse.ok(loginPayload(loadUser(username)));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        String username = Db.required(body, "username");
        String password = Db.required(body, "password");
        Map<String, Object> user = loadUser(username);
        if (!Db.sha256(password).equals(user.get("password_hash"))) {
            throw new BusinessException("账号或密码错误");
        }
        if (!"ENABLED".equals(String.valueOf(user.get("account_status")))) {
            throw new BusinessException("账号已被禁用");
        }
        return ApiResponse.ok(loginPayload(user));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> me() {
        return ApiResponse.ok(UserContext.get());
    }

    private Map<String, Object> loadUser(String username) {
        Map<String, Object> user = db.one("""
                select user_id, student_no, username, password_hash, real_name, phone, email, account_status
                from user_account where username = ? or student_no = ?
                """, username, username);
        List<Map<String, Object>> roleRows = db.jdbc().queryForList("""
                select r.role_code
                from user_role ur join role r on ur.role_id = r.role_id
                where ur.user_id = ?
                """, user.get("user_id"));
        user.put("roles", Db.rolesFromRows(roleRows));
        return user;
    }

    private Map<String, Object> loginPayload(Map<String, Object> user) {
        @SuppressWarnings("unchecked")
        Set<String> roles = new TreeSet<>((List<String>) user.get("roles"));
        CurrentUser current = new CurrentUser(
                ((Number) user.get("user_id")).longValue(),
                String.valueOf(user.get("username")),
                String.valueOf(user.get("real_name")),
                roles
        );
        return Map.of("token", tokenService.create(current), "user", current);
    }
}
