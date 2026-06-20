package com.example.campus.auth;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.AuthRequests.LoginRequest;
import com.example.campus.dto.AuthRequests.RegisterRequest;
import com.example.campus.enums.RoleCode;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.TokenService;
import com.example.campus.security.UserContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录、注册和当前用户信息接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final Db db;
    private final TokenService tokenService;

    /**
     * 注入认证模块使用的数据库工具和令牌服务。
     */
    public AuthController(Db db, TokenService tokenService) {
        this.db = db;
        this.tokenService = tokenService;
    }

    /**
     * 注册普通学生账号，并立即返回登录令牌。
     */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        String username = Db.require(request.username(), "username");
        String password = Db.require(request.password(), "password");
        String studentNo = Db.require(request.studentNo(), "studentNo");
        String realName = Db.require(request.realName(), "realName");

        Long userId = db.insert("""
                insert into user_account(student_no, username, password_hash, real_name, phone, email)
                values(?, ?, ?, ?, ?, ?)
                """, studentNo, username, Db.sha256(password), realName, request.phone(), request.email());
        db.jdbc().update("""
                insert into student_profile(user_id, college, major, class_name, grade)
                values(?, ?, ?, ?, ?)
                """, userId, request.college(), request.major(), request.className(), request.grade());

        // 所有新注册用户默认都是学生；更高权限角色必须
        // 通过管理员审核授予，保证权限来源可追溯。
        db.jdbc().update("""
                insert into user_role(user_id, role_id)
                select ?, role_id from role where role_code = 'STUDENT'
                """, userId);

        return ApiResponse.ok(loginPayload(loadUser(username)));
    }

    /**
     * 支持使用用户名或学号登录。
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        String username = Db.require(request.username(), "username");
        String password = Db.require(request.password(), "password");
        Map<String, Object> user = loadUser(username);

        if (!Db.sha256(password).equals(user.get("password_hash"))) {
            throw new BusinessException("账号或密码错误");
        }
        if (!"ENABLED".equals(String.valueOf(user.get("account_status")))) {
            throw new BusinessException("账号已被禁用");
        }
        return ApiResponse.ok(loginPayload(user));
    }

    /**
     * 返回当前令牌代表的用户信息。
     */
    @GetMapping("/me")
    public ApiResponse<CurrentUser> me() {
        return ApiResponse.ok(UserContext.get());
    }

    /**
     * 加载注册后自动登录和普通登录共同需要的账号与角色数据。
     */
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

    /**
     * 只把主角色返回给前端，确保首页和
     * 菜单选择稳定一致：管理员优先，其次是组织负责人，最后是学生。
     *
     * @param user 已补充角色列表的数据库用户行
     * @return 返回给前端的令牌和当前用户信息
     */
    private Map<String, Object> loginPayload(Map<String, Object> user) {
        @SuppressWarnings("unchecked")
        RoleCode primaryRole = RoleCode.primaryOf((List<String>) user.get("roles"));
        CurrentUser current = new CurrentUser(
                ((Number) user.get("user_id")).longValue(),
                String.valueOf(user.get("username")),
                String.valueOf(user.get("real_name")),
                Set.of(primaryRole.name())
        );
        return Map.of("token", tokenService.create(current), "user", current);
    }
}
