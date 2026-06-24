package com.example.campus.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 数据库完整性约束测试。
 *
 * 测试过程设计：
 * 1. 每个测试方法执行前使用 reset.sql 重建测试库，并写入多组真实业务数据。
 * 2. 测试方法直接执行违反约束的 SQL，覆盖唯一约束、外键约束、非空约束、
 *    检查约束和枚举取值约束。
 * 3. 断言数据库抛出 DataAccessException，并再次查询表数据，
 *    验证非法数据没有写入，原始数据没有被破坏。
 */
@ActiveProfiles("test")
@SpringBootTest
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class IntegrityConstraintTest {

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * 验证用户、组织、报名和积分规则的唯一约束。
     */
    @Test
    void 唯一约束_重复业务键写入失败且数据不变() {
        String existingOrgName = jdbc.queryForObject(
                "select org_name from organization where org_id=1", String.class);

        assertInsertViolationAndCountUnchanged("用户账号表不允许重复用户名", "user_account", () ->
                jdbc.update("""
                        insert into user_account(student_no, username, password_hash, real_name)
                        values('S2099001', 'student1', sha2('123456', 256), '重复用户名')
                        """));

        assertInsertViolationAndCountUnchanged("用户账号表不允许重复学号", "user_account", () ->
                jdbc.update("""
                        insert into user_account(student_no, username, password_hash, real_name)
                        values('S2023001', 'unique_user_for_student_no', sha2('123456', 256), '重复学号')
                        """));

        assertInsertViolationAndCountUnchanged("组织表不允许重复组织名称", "organization", () ->
                jdbc.update("""
                        insert into organization(org_name, org_type, principal_user_id, org_status)
                        values(?, 'CLUB', 4, 'ACTIVE')
                        """, existingOrgName));

        assertInsertViolationAndCountUnchanged("同一学生不能重复报名同一活动", "registration", () ->
                jdbc.update("""
                        insert into registration(activity_id, user_id, registration_status, checkin_status)
                        values(1, 2, 'VALID', 'NOT_CHECKED')
                        """));

        assertInsertViolationAndCountUnchanged("同一活动类型只能存在一条启用中的积分规则", "score_rule", () ->
                jdbc.update("""
                        insert into score_rule(type_id, base_score, normal_weight, member_weight, leader_weight, rule_desc, effective_status)
                        values(1, 1.00, 1.00, 1.20, 1.50, '重复启用规则', 'ENABLED')
                        """));
    }

    /**
     * 验证外键约束，确保所有从表数据必须引用真实存在的主表数据。
     */
    @Test
    void 外键约束_不存在的引用写入失败且数据不变() {
        assertInsertViolationAndCountUnchanged("学生档案必须引用已存在的用户", "student_profile", () ->
                jdbc.update("""
                        insert into student_profile(user_id, college, major)
                        values(9999, '测试学院', '测试专业')
                        """));

        assertInsertViolationAndCountUnchanged("组织负责人必须引用已存在的用户", "organization", () ->
                jdbc.update("""
                        insert into organization(org_name, org_type, principal_user_id, org_status)
                        values('外键测试组织', 'CLUB', 9999, 'ACTIVE')
                        """));

        assertInsertViolationAndCountUnchanged("活动必须引用已存在的组织", "activity", () ->
                jdbc.update("""
                        insert into activity(activity_name, type_id, org_id, start_time, end_time, location,
                            registration_deadline, capacity, registered_count, rule_id, activity_status, created_by)
                        values('外键测试活动', 1, 9999, '2026-09-01 09:00:00', '2026-09-01 11:00:00', '测试地点',
                            '2026-08-31 18:00:00', 20, 0, 1, 'OPEN', 4)
                        """));

        assertInsertViolationAndCountUnchanged("报名记录必须引用已存在的活动", "registration", () ->
                jdbc.update("""
                        insert into registration(activity_id, user_id, registration_status, checkin_status)
                        values(9999, 2, 'VALID', 'NOT_CHECKED')
                        """));

        assertInsertViolationAndCountUnchanged("积分记录必须引用已存在的积分规则", "score_record", () ->
                jdbc.update("""
                        insert into score_record(user_id, activity_id, rule_id, identity_type, base_score,
                            identity_weight, final_score, audit_status, submitter_id)
                        values(2, 2, 9999, 'NORMAL', 2.00, 1.00, 2.00, 'PENDING', 5)
                        """));
    }

    /**
     * 验证活动表的检查约束，确保容量、报名人数和时间顺序合法。
     */
    @Test
    void 检查约束_活动容量与时间不合法时写入失败() {
        assertInsertViolationAndCountUnchanged("活动容量必须大于零", "activity", () ->
                jdbc.update("""
                        insert into activity(activity_name, type_id, org_id, start_time, end_time, location,
                            registration_deadline, capacity, registered_count, rule_id, activity_status, created_by)
                        values('容量为零测试', 1, 1, '2026-09-01 09:00:00', '2026-09-01 11:00:00', '测试地点',
                            '2026-08-31 18:00:00', 0, 0, 1, 'DRAFT', 4)
                        """));

        assertInsertViolationAndCountUnchanged("已报名人数不能超过容量", "activity", () ->
                jdbc.update("""
                        insert into activity(activity_name, type_id, org_id, start_time, end_time, location,
                            registration_deadline, capacity, registered_count, rule_id, activity_status, created_by)
                        values('人数超容量测试', 1, 1, '2026-09-01 09:00:00', '2026-09-01 11:00:00', '测试地点',
                            '2026-08-31 18:00:00', 10, 11, 1, 'DRAFT', 4)
                        """));

        assertInsertViolationAndCountUnchanged("活动结束时间不能早于开始时间", "activity", () ->
                jdbc.update("""
                        insert into activity(activity_name, type_id, org_id, start_time, end_time, location,
                            registration_deadline, capacity, registered_count, rule_id, activity_status, created_by)
                        values('结束早于开始测试', 1, 1, '2026-09-01 11:00:00', '2026-09-01 09:00:00', '测试地点',
                            '2026-08-31 18:00:00', 20, 0, 1, 'DRAFT', 4)
                        """));

        assertInsertViolationAndCountUnchanged("报名截止时间不能晚于活动开始时间", "activity", () ->
                jdbc.update("""
                        insert into activity(activity_name, type_id, org_id, start_time, end_time, location,
                            registration_deadline, capacity, registered_count, rule_id, activity_status, created_by)
                        values('截止晚于开始测试', 1, 1, '2026-09-01 09:00:00', '2026-09-01 11:00:00', '测试地点',
                            '2026-09-01 10:00:00', 20, 0, 1, 'DRAFT', 4)
                        """));
    }

    /**
     * 验证积分相关检查约束，确保分值和权重不能为负数或零。
     */
    @Test
    void 检查约束_积分分值与权重不合法时写入失败() {
        assertInsertViolationAndCountUnchanged("积分规则基础分不能为负数", "score_rule", () ->
                jdbc.update("""
                        insert into score_rule(type_id, base_score, normal_weight, member_weight, leader_weight, rule_desc, effective_status)
                        values(1, -1.00, 1.00, 1.20, 1.50, '负数基础分测试', 'DISABLED')
                        """));

        assertInsertViolationAndCountUnchanged("普通参与者权重必须大于零", "score_rule", () ->
                jdbc.update("""
                        insert into score_rule(type_id, base_score, normal_weight, member_weight, leader_weight, rule_desc, effective_status)
                        values(1, 1.00, 0.00, 1.20, 1.50, '零权重测试', 'DISABLED')
                        """));

        assertInsertViolationAndCountUnchanged("积分记录最终分不能为负数", "score_record", () ->
                jdbc.update("""
                        insert into score_record(user_id, activity_id, rule_id, identity_type, base_score,
                            identity_weight, final_score, audit_status, submitter_id)
                        values(2, 2, 2, 'NORMAL', 2.00, 1.00, -2.00, 'PENDING', 5)
                        """));

        assertInsertViolationAndCountUnchanged("积分记录身份权重必须大于零", "score_record", () ->
                jdbc.update("""
                        insert into score_record(user_id, activity_id, rule_id, identity_type, base_score,
                            identity_weight, final_score, audit_status, submitter_id)
                        values(2, 2, 2, 'NORMAL', 2.00, 0.00, 2.00, 'PENDING', 5)
                        """));
    }

    /**
     * 验证枚举取值约束，非法状态值不能进入数据库。
     */
    @Test
    void 枚举约束_非法状态值写入失败且原值不变() {
        assertInsertViolationAndCountUnchanged("账号状态只能取启用或禁用", "user_account", () ->
                jdbc.update("""
                        insert into user_account(student_no, username, password_hash, real_name, account_status)
                        values('S2099002', 'bad_status_user', sha2('123456', 256), '非法账号状态', 'LOCKED')
                        """));

        String beforeActivityStatus = jdbc.queryForObject(
                "select activity_status from activity where activity_id=1", String.class);
        assertThatThrownBy(() -> jdbc.update(
                "update activity set activity_status='ARCHIVED' where activity_id=1"))
                .as("活动状态只能取预设枚举值")
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject(
                "select activity_status from activity where activity_id=1", String.class))
                .isEqualTo(beforeActivityStatus);

        assertInsertViolationAndCountUnchanged("组织成员角色只能取成员或负责人", "organization_member", () ->
                jdbc.update("""
                        insert into organization_member(org_id, user_id, member_role, join_status)
                        values(2, 2, 'CAPTAIN', 'APPROVED')
                        """));
    }

    /**
     * 验证非空约束，核心业务字段不能为空。
     */
    @Test
    void 非空约束_核心字段为空时写入失败且数据不变() {
        assertInsertViolationAndCountUnchanged("用户名不能为空", "user_account", () ->
                jdbc.update("""
                        insert into user_account(student_no, username, password_hash, real_name)
                        values('S2099003', null, sha2('123456', 256), '空用户名')
                        """));

        assertInsertViolationAndCountUnchanged("活动名称不能为空", "activity", () ->
                jdbc.update("""
                        insert into activity(activity_name, type_id, org_id, start_time, end_time, location,
                            registration_deadline, capacity, registered_count, rule_id, activity_status, created_by)
                        values(null, 1, 1, '2026-09-01 09:00:00', '2026-09-01 11:00:00', '测试地点',
                            '2026-08-31 18:00:00', 20, 0, 1, 'DRAFT', 4)
                        """));

        assertInsertViolationAndCountUnchanged("报名用户不能为空", "registration", () ->
                jdbc.update("""
                        insert into registration(activity_id, user_id, registration_status, checkin_status)
                        values(1, null, 'VALID', 'NOT_CHECKED')
                        """));
    }

    /**
     * 断言非法插入会失败，并验证目标表行数没有变化。
     */
    private void assertInsertViolationAndCountUnchanged(String caseName, String tableName, ThrowingCallable operation) {
        Integer before = jdbc.queryForObject("select count(*) from " + tableName, Integer.class);
        assertThatThrownBy(operation)
                .as(caseName)
                .isInstanceOf(DataAccessException.class);
        Integer after = jdbc.queryForObject("select count(*) from " + tableName, Integer.class);
        assertThat(after).as(caseName + "，失败后表数据量应保持不变").isEqualTo(before);
    }
}
