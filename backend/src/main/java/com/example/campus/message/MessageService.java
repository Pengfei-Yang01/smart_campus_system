package com.example.campus.message;

import com.example.campus.common.Db;
import com.example.campus.enums.MessageCategory;
import com.example.campus.enums.NoticePriority;
import com.example.campus.enums.NoticeTargetRole;
import org.springframework.stereotype.Service;

/**
 * 消息中心写入服务。
 *
 * 控制器在产生审批结果、公告、评价回复等业务事件时调用这里，
 * 统一向 user_message 表写入站内消息，前端消息中心只需要读取一张收件箱表。
 */
@Service
public class MessageService {
    private final Db db;

    /**
     * 注入数据库访问工具。
     */
    public MessageService(Db db) {
        this.db = db;
    }

    /**
     * 给单个用户发送站内消息。
     *
     * @param recipientId 接收人用户编号
     * @param title 消息标题
     * @param content 消息正文
     * @param category 消息分类
     * @param sourceType 来源业务类型
     * @param sourceId 来源业务主键
     */
    public void notifyUser(Long recipientId, String title, String content,
                           MessageCategory category, String sourceType, Long sourceId) {
        if (recipientId == null) {
            return;
        }
        db.jdbc().update("""
                insert into user_message(recipient_id, title, content, category, source_type, source_id)
                values(?, ?, ?, ?, ?, ?)
                """, recipientId, title, content, category.name(), sourceType, sourceId);
    }

    /**
     * 给某个主角色范围内的所有当前用户发送业务提醒。
     *
     * 该方法不创建 notice 公告记录，只用于待办、审批、评价回复等系统事件。
     */
    public void notifyRole(NoticeTargetRole targetRole, String title, String content,
                           MessageCategory category, String sourceType, Long sourceId) {
        String condition = targetCondition(targetRole == null ? NoticeTargetRole.ALL : targetRole);
        db.jdbc().update("""
                insert into user_message(recipient_id, title, content, category, source_type, source_id)
                select u.user_id, ?, ?, ?, ?, ?
                from user_account u
                where u.account_status='ENABLED'
                """ + condition, title, content, category.name(), sourceType, sourceId);
    }

    /**
     * 管理员发布公告，并给当前符合目标角色的用户生成收件箱消息。
     *
     * @param publisherId 发布公告的管理员编号
     * @param title 公告标题
     * @param content 公告正文
     * @param targetRole 目标主角色
     * @param priority 公告优先级
     * @return 公告主键
     */
    public Long publishNotice(Long publisherId, String title, String content,
                              NoticeTargetRole targetRole, NoticePriority priority) {
        NoticeTargetRole role = targetRole == null ? NoticeTargetRole.ALL : targetRole;
        NoticePriority level = priority == null ? NoticePriority.NORMAL : priority;
        Long noticeId = db.insert("""
                insert into notice(title, content, target_role, priority, publisher_id)
                values(?, ?, ?, ?, ?)
                """, title, content, role.name(), level.name(), publisherId);
        fanoutNotice(noticeId, title, content, role);
        return noticeId;
    }

    /**
     * 把公告转换成每个接收人的站内消息。
     *
     * 数据库里一个账号可能同时拥有 STUDENT 和更高权限角色，
     * 这里按系统主角色规则筛选目标用户：管理员优先，其次负责人，最后学生。
     */
    private void fanoutNotice(Long noticeId, String title, String content, NoticeTargetRole targetRole) {
        String condition = targetCondition(targetRole);
        db.jdbc().update("""
                insert into user_message(recipient_id, title, content, category, source_type, source_id, source_notice_id)
                select u.user_id, ?, ?, 'NOTICE', 'NOTICE', ?, ?
                from user_account u
                where u.account_status='ENABLED'
                """ + condition, title, content, noticeId, noticeId);
    }

    /**
     * 根据目标主角色拼接用户筛选条件。
     */
    private String targetCondition(NoticeTargetRole targetRole) {
        return switch (targetRole) {
            case ALL -> "";
            case ADMIN -> """
                     and exists (
                         select 1 from user_role ur join role r on ur.role_id = r.role_id
                         where ur.user_id = u.user_id and r.role_code='ADMIN'
                     )
                    """;
            case ORG_LEADER -> """
                     and exists (
                         select 1 from user_role ur join role r on ur.role_id = r.role_id
                         where ur.user_id = u.user_id and r.role_code='ORG_LEADER'
                     )
                     and not exists (
                         select 1 from user_role ur join role r on ur.role_id = r.role_id
                         where ur.user_id = u.user_id and r.role_code='ADMIN'
                     )
                    """;
            case STUDENT -> """
                     and not exists (
                         select 1 from user_role ur join role r on ur.role_id = r.role_id
                         where ur.user_id = u.user_id and r.role_code in ('ADMIN','ORG_LEADER')
                     )
                    """;
        };
    }
}
