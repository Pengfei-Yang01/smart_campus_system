package com.example.campus.student;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.StudentRequests.LeaderApplyRequest;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.UserContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端首页、申请和个人记录接口。
 */
@RestController
@RequestMapping("/api")
public class StudentController {
    private final Db db;

    /**
     * 注入学生端接口使用的数据库工具类。
     */
    public StudentController(Db db) {
        this.db = db;
    }

    /**
     * 返回学生首页数据：近期活动、报名数量、
     * 待审核积分数量、已通过积分总数和最新负责人申请。
     */
    @GetMapping("/dashboard/student")
    public ApiResponse<Map<String, Object>> dashboard() {
        CurrentUser user = UserContext.get();
        Map<String, Object> leaderApply = db.maybeOne("""
                select * from leader_apply
                where user_id = ?
                order by applied_at desc limit 1
                """, user.userId());
        return ApiResponse.ok(Map.of(
                "recentActivities", db.jdbc().queryForList("""
                        select a.*, t.type_name, o.org_name
                        from activity a
                        join activity_type t on a.type_id = t.type_id
                        join organization o on a.org_id = o.org_id
                        where a.activity_status in ('OPEN','CLOSED')
                        order by a.start_time limit 6
                        """),
                "registrationCount", db.count("select count(*) from registration where user_id = ? and registration_status='VALID'", user.userId()),
                "pendingScoreCount", db.count("select count(*) from score_record where user_id = ? and audit_status='PENDING'", user.userId()),
                "approvedScore", db.maybeOne("select coalesce(sum(final_score),0) total from score_record where user_id = ? and audit_status='APPROVED'", user.userId()),
                "leaderApply", leaderApply == null ? Map.of() : leaderApply
        ));
    }

    /**
     * 提交当前学生成为组织负责人的申请。
     */
    @PostMapping("/students/leader-apply")
    public ApiResponse<Object> applyLeader(@RequestBody LeaderApplyRequest request) {
        CurrentUser user = UserContext.get();
        if (user.isLeader() || user.isAdmin()) {
            throw new BusinessException("当前角色不需要申请组织负责人");
        }
        if (db.count("select count(*) from leader_apply where user_id=? and status='PENDING'", user.userId()) > 0) {
            throw new BusinessException("你已有待审核的负责人申请");
        }
        Long id = db.insert("""
                insert into leader_apply(user_id, apply_reason, contact, experience)
                values(?, ?, ?, ?)
                """, user.userId(), Db.require(request.applyReason(), "applyReason"), request.contact(), request.experience());
        return ApiResponse.ok(Map.of("applyId", id));
    }

    /**
     * 查询当前用户的活动报名记录。
     */
    @GetMapping("/me/registrations")
    public ApiResponse<Object> myRegistrations() {
        CurrentUser user = UserContext.get();
        return ApiResponse.ok(db.jdbc().queryForList("""
                select r.*, a.activity_name, a.start_time, a.location, a.activity_status, o.org_name, t.type_name
                from registration r
                join activity a on r.activity_id = a.activity_id
                join organization o on a.org_id = o.org_id
                join activity_type t on a.type_id = t.type_id
                where r.user_id = ?
                order by r.registered_at desc
                """, user.userId()));
    }

    /**
     * 查询当前用户的积分记录。
     */
    @GetMapping("/me/scores")
    public ApiResponse<Object> myScores() {
        CurrentUser user = UserContext.get();
        return ApiResponse.ok(db.jdbc().queryForList("""
                select sr.*, a.activity_name, o.org_name, t.type_name
                from score_record sr
                join activity a on sr.activity_id = a.activity_id
                join organization o on a.org_id = o.org_id
                join activity_type t on a.type_id = t.type_id
                where sr.user_id = ?
                order by sr.submitted_at desc
                """, user.userId()));
    }
}
