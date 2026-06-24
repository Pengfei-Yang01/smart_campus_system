package com.example.campus.ai;

import com.example.campus.common.Db;
import com.example.campus.security.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Builds a compact business-data summary that the model can use safely.
 */
@Service
public class AiContextService {
    private final Db db;

    public AiContextService(Db db) {
        this.db = db;
    }

    public String build(CurrentUser user) {
        StringBuilder text = new StringBuilder();
        text.append("当前用户：").append(user.realName()).append("（").append(user.username()).append("）\n");
        text.append("角色：").append(user.roles()).append("\n\n");

        appendRows(text, "最近开放活动", db.jdbc().queryForList("""
                select a.activity_name, t.type_name, o.org_name, a.start_time, a.location,
                       greatest(a.capacity - a.registered_count, 0) remaining_count
                from activity a
                join activity_type t on a.type_id = t.type_id
                join organization o on a.org_id = o.org_id
                where a.activity_status='OPEN'
                order by a.start_time
                limit 5
                """));

        appendRows(text, "我的最近报名", db.jdbc().queryForList("""
                select a.activity_name, r.registration_status, r.checkin_status, a.activity_status
                from registration r
                join activity a on r.activity_id = a.activity_id
                where r.user_id = ?
                order by r.registered_at desc
                limit 5
                """, user.userId()));

        appendRows(text, "我的积分概览", db.jdbc().queryForList("""
                select coalesce(sum(case when audit_status='APPROVED' then final_score else 0 end),0) approved_score,
                       sum(case when audit_status='PENDING' then 1 else 0 end) pending_count
                from score_record
                where user_id = ?
                """, user.userId()));

        appendRows(text, "我的最近积分", db.jdbc().queryForList("""
                select a.activity_name, sr.final_score, sr.audit_status
                from score_record sr
                join activity a on sr.activity_id = a.activity_id
                where sr.user_id = ?
                order by sr.submitted_at desc
                limit 5
                """, user.userId()));

        if (user.isLeader()) {
            appendRows(text, "我负责的组织", db.jdbc().queryForList("""
                    select org_id, org_name, org_status
                    from organization
                    where principal_user_id = ?
                    limit 5
                    """, user.userId()));
            appendRows(text, "负责人待办", db.jdbc().queryForList("""
                    select count(*) pending_join_count
                    from organization_member om
                    join organization o on om.org_id = o.org_id
                    where o.principal_user_id = ? and om.join_status='PENDING'
                    """, user.userId()));
        }

        if (user.isAdmin()) {
            appendRows(text, "管理员平台摘要", db.jdbc().queryForList("""
                    select
                      (select count(*) from user_account) user_count,
                      (select count(*) from activity) activity_count,
                      (select count(*) from organization) organization_count,
                      (select count(*) from leader_apply where status='PENDING') pending_leader_count,
                      (select count(*) from organization_apply where status='PENDING') pending_org_count,
                      (select count(*) from score_record where audit_status='PENDING') pending_score_count
                    """));
        }

        return text.toString();
    }

    private void appendRows(StringBuilder text, String title, List<Map<String, Object>> rows) {
        text.append("【").append(title).append("】\n");
        if (rows.isEmpty()) {
            text.append("暂无数据\n\n");
            return;
        }
        for (Map<String, Object> row : rows) {
            text.append("- ").append(row).append("\n");
        }
        text.append("\n");
    }
}
