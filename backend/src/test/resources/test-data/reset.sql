-- ============================================================
-- 测试数据库重置脚本
-- 在每个测试类运行前执行，保证纯净的测试环境
-- ============================================================
set foreign_key_checks = 0;
drop table if exists ai_qa_record;
drop table if exists score_record;
drop table if exists registration;
drop table if exists activity;
drop table if exists score_rule;
drop table if exists activity_type;
drop table if exists organization_member;
drop table if exists organization_apply;
drop table if exists organization;
drop table if exists leader_apply;
drop table if exists user_role;
drop table if exists student_profile;
drop table if exists user_account;
drop table if exists role;
set foreign_key_checks = 1;

-- ======================== 建表 ========================

create table role (
  role_id bigint primary key auto_increment,
  role_code varchar(32) not null unique,
  role_name varchar(50) not null,
  description varchar(255)
) engine=InnoDB default charset=utf8mb4;

create table user_account (
  user_id bigint primary key auto_increment,
  student_no varchar(30) not null unique,
  username varchar(50) not null unique,
  password_hash varchar(255) not null,
  real_name varchar(50) not null,
  phone varchar(20),
  email varchar(100),
  account_status enum('ENABLED','DISABLED') not null default 'ENABLED',
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp
) engine=InnoDB default charset=utf8mb4;

create table student_profile (
  user_id bigint primary key,
  college varchar(80),
  major varchar(80),
  class_name varchar(40),
  grade varchar(20),
  constraint fk_student_profile_user foreign key (user_id) references user_account(user_id)
) engine=InnoDB default charset=utf8mb4;

create table user_role (
  user_id bigint not null,
  role_id bigint not null,
  granted_at datetime not null default current_timestamp,
  primary key (user_id, role_id),
  constraint fk_user_role_user foreign key (user_id) references user_account(user_id),
  constraint fk_user_role_role foreign key (role_id) references role(role_id)
) engine=InnoDB default charset=utf8mb4;

create table leader_apply (
  apply_id bigint primary key auto_increment,
  user_id bigint not null,
  apply_reason varchar(500) not null,
  contact varchar(100),
  experience varchar(500),
  status enum('PENDING','APPROVED','REJECTED') not null default 'PENDING',
  reviewer_id bigint,
  reject_reason varchar(300),
  applied_at datetime not null default current_timestamp,
  reviewed_at datetime,
  index idx_leader_apply_user_status(user_id, status),
  constraint fk_leader_apply_user foreign key (user_id) references user_account(user_id),
  constraint fk_leader_apply_reviewer foreign key (reviewer_id) references user_account(user_id)
) engine=InnoDB default charset=utf8mb4;

create table organization (
  org_id bigint primary key auto_increment,
  org_name varchar(100) not null unique,
  org_type enum('CLUB','COLLEGE','UNIVERSITY','OTHER') not null,
  description text,
  contact varchar(100),
  principal_user_id bigint not null,
  org_status enum('PENDING','ACTIVE','DISABLED') not null default 'ACTIVE',
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  constraint fk_organization_principal foreign key (principal_user_id) references user_account(user_id)
) engine=InnoDB default charset=utf8mb4;

create table organization_apply (
  org_apply_id bigint primary key auto_increment,
  applicant_id bigint not null,
  org_name varchar(100) not null,
  org_type enum('CLUB','COLLEGE','UNIVERSITY','OTHER') not null,
  description text,
  apply_reason varchar(500) not null,
  contact varchar(100),
  status enum('PENDING','APPROVED','REJECTED') not null default 'PENDING',
  reviewer_id bigint,
  reject_reason varchar(300),
  applied_at datetime not null default current_timestamp,
  reviewed_at datetime,
  index idx_org_apply_status(status),
  constraint fk_org_apply_applicant foreign key (applicant_id) references user_account(user_id),
  constraint fk_org_apply_reviewer foreign key (reviewer_id) references user_account(user_id)
) engine=InnoDB default charset=utf8mb4;

create table organization_member (
  org_id bigint not null,
  user_id bigint not null,
  member_role enum('MEMBER','LEADER') not null default 'MEMBER',
  join_status enum('PENDING','APPROVED','REJECTED','QUIT') not null default 'PENDING',
  apply_reason varchar(300),
  reject_reason varchar(300),
  joined_at datetime not null default current_timestamp,
  primary key (org_id, user_id),
  constraint fk_org_member_org foreign key (org_id) references organization(org_id),
  constraint fk_org_member_user foreign key (user_id) references user_account(user_id)
) engine=InnoDB default charset=utf8mb4;

create table activity_type (
  type_id bigint primary key auto_increment,
  type_code varchar(32) not null unique,
  type_name varchar(50) not null
) engine=InnoDB default charset=utf8mb4;

create table score_rule (
  rule_id bigint primary key auto_increment,
  type_id bigint not null,
  base_score decimal(6,2) not null check (base_score >= 0),
  normal_weight decimal(4,2) not null default 1.00 check (normal_weight > 0),
  member_weight decimal(4,2) not null default 1.20 check (member_weight > 0),
  leader_weight decimal(4,2) not null default 1.50 check (leader_weight > 0),
  rule_desc varchar(300),
  effective_status enum('ENABLED','DISABLED') not null default 'ENABLED',
  enabled_type_id bigint generated always as (case when effective_status = 'ENABLED' then type_id else null end) stored,
  unique key uk_score_rule_enabled_type(enabled_type_id),
  constraint fk_score_rule_type foreign key (type_id) references activity_type(type_id)
) engine=InnoDB default charset=utf8mb4;

create table activity (
  activity_id bigint primary key auto_increment,
  activity_name varchar(120) not null,
  type_id bigint not null,
  org_id bigint not null,
  start_time datetime not null,
  end_time datetime not null,
  location varchar(150) not null,
  registration_deadline datetime not null,
  capacity int not null check (capacity > 0),
  registered_count int not null default 0 check (registered_count >= 0),
  description text,
  requirement text,
  base_score_override decimal(6,2),
  rule_id bigint,
  activity_status enum('DRAFT','OPEN','CLOSED','FINISHED','OFFLINE') not null default 'DRAFT',
  created_by bigint not null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  check (end_time >= start_time),
  check (registration_deadline <= start_time),
  check (registered_count <= capacity),
  index idx_activity_status_type(activity_status, type_id),
  constraint fk_activity_type foreign key (type_id) references activity_type(type_id),
  constraint fk_activity_org foreign key (org_id) references organization(org_id),
  constraint fk_activity_rule foreign key (rule_id) references score_rule(rule_id),
  constraint fk_activity_creator foreign key (created_by) references user_account(user_id)
) engine=InnoDB default charset=utf8mb4;

create table registration (
  registration_id bigint primary key auto_increment,
  activity_id bigint not null,
  user_id bigint not null,
  registered_at datetime not null default current_timestamp,
  registration_status enum('VALID','CANCELLED') not null default 'VALID',
  checkin_status enum('NOT_CHECKED','CHECKED','ABSENT') not null default 'NOT_CHECKED',
  unique key uk_registration_activity_user(activity_id, user_id),
  constraint fk_registration_activity foreign key (activity_id) references activity(activity_id),
  constraint fk_registration_user foreign key (user_id) references user_account(user_id)
) engine=InnoDB default charset=utf8mb4;

create table score_record (
  score_id bigint primary key auto_increment,
  user_id bigint not null,
  activity_id bigint not null,
  rule_id bigint not null,
  identity_type enum('NORMAL','ORG_MEMBER','ORG_LEADER') not null,
  base_score decimal(6,2) not null check (base_score >= 0),
  identity_weight decimal(4,2) not null check (identity_weight > 0),
  final_score decimal(6,2) not null check (final_score >= 0),
  audit_status enum('PENDING','APPROVED','REJECTED') not null default 'PENDING',
  submitter_id bigint,
  reviewer_id bigint,
  reject_reason varchar(300),
  submitted_at datetime not null default current_timestamp,
  reviewed_at datetime,
  unique key uk_score_activity_user(activity_id, user_id),
  constraint fk_score_user foreign key (user_id) references user_account(user_id),
  constraint fk_score_activity foreign key (activity_id) references activity(activity_id),
  constraint fk_score_rule foreign key (rule_id) references score_rule(rule_id),
  constraint fk_score_submitter foreign key (submitter_id) references user_account(user_id),
  constraint fk_score_reviewer foreign key (reviewer_id) references user_account(user_id)
) engine=InnoDB default charset=utf8mb4;

create table ai_qa_record (
  qa_id bigint primary key auto_increment,
  user_id bigint not null,
  question text not null,
  answer text not null,
  model_name varchar(80) not null default 'not-enabled',
  prompt_tokens int default 0,
  completion_tokens int default 0,
  cost_ms int,
  called_at datetime not null default current_timestamp,
  index idx_ai_qa_user_time(user_id, called_at),
  constraint fk_ai_qa_user foreign key (user_id) references user_account(user_id)
) engine=InnoDB default charset=utf8mb4;

-- ======================== 种子数据 ========================

-- 角色 (id=1 STUDENT, 2 ORG_LEADER, 3 ADMIN)
insert into role(role_id, role_code, role_name, description) values
(1, 'STUDENT', '普通学生', '浏览活动、报名、申请加入组织、查看个人积分'),
(2, 'ORG_LEADER', '组织负责人', '管理本人负责组织、发布活动、审批成员、录入积分'),
(3, 'ADMIN', '系统管理员', '管理学生、组织审批、负责人审批、积分审核和系统统计');

-- 用户账号: id=1 admin, 2 student1, 3 student2, 4 leader1, 5 leader2, 6 disabled_stu
insert into user_account(user_id, student_no, username, password_hash, real_name, phone, email, account_status) values
(1, 'A0001', 'admin', sha2('123456', 256), '系统管理员', '13800000000', 'admin@campus.local', 'ENABLED'),
(2, 'S2023001', 'student1', sha2('123456', 256), '林小北', '13800000001', 'student1@campus.local', 'ENABLED'),
(3, 'S2023002', 'student2', sha2('123456', 256), '周明远', '13800000002', 'student2@campus.local', 'ENABLED'),
(4, 'S2023003', 'leader1', sha2('123456', 256), '陈思源', '13800000003', 'leader1@campus.local', 'ENABLED'),
(5, 'S2023004', 'leader2', sha2('123456', 256), '许安然', '13800000004', 'leader2@campus.local', 'ENABLED'),
(6, 'S2023005', 'disabled_stu', sha2('123456', 256), '已禁用学生', '13800000005', 'disabled@campus.local', 'DISABLED');

-- 学生档案
insert into student_profile(user_id, college, major, class_name, grade) values
(1, '软件学院', '软件工程', '软工2311104', '2023'),
(2, '软件学院', '软件工程', '软工2311104', '2023'),
(3, '计算机学院', '人工智能', '智能2302', '2023'),
(4, '软件学院', '软件工程', '软工2210', '2022'),
(5, '经济管理学院', '信息管理', '信管2201', '2022'),
(6, '软件学院', '软件工程', '软工2311104', '2023');

-- 用户角色
insert into user_role(user_id, role_id) values
(1, 1), (1, 3),  -- admin = STUDENT + ADMIN
(2, 1),           -- student1 = STUDENT
(3, 1),           -- student2 = STUDENT
(4, 1), (4, 2),  -- leader1 = STUDENT + ORG_LEADER
(5, 1), (5, 2),  -- leader2 = STUDENT + ORG_LEADER
(6, 1);           -- disabled_stu = STUDENT

-- 负责人申请: student1 (id=2) 有一个 PENDING 申请
insert into leader_apply(apply_id, user_id, apply_reason, contact, experience, status) values
(1, 2, '长期参与社团活动，希望负责活动组织与学分认定。', 'student1@campus.local', '曾任学习委员与社团活动负责人。', 'PENDING');

-- 组织: id=1 ACTIVE(leader1), id=2 ACTIVE(leader2), id=3 DISABLED(leader1)
insert into organization(org_id, org_name, org_type, description, contact, principal_user_id, org_status) values
(1, '软件创新协会', 'CLUB', '面向全校学生开展技术分享、编程训练和项目实践。', 'softclub@campus.local', 4, 'ACTIVE'),
(2, '青年志愿者服务队', 'UNIVERSITY', '组织公益服务、社区实践和校园志愿活动。', 'volunteer@campus.local', 5, 'ACTIVE'),
(3, '测试停用组织', 'OTHER', '用于测试停用组织场景', 'test@campus.local', 4, 'DISABLED');

-- 组织成员
insert into organization_member(org_id, user_id, member_role, join_status, apply_reason) values
(1, 4, 'LEADER', 'APPROVED', null),
(1, 2, 'MEMBER', 'APPROVED', '希望参与项目实践。'),
(1, 3, 'MEMBER', 'PENDING', '想加入技术分享活动。'),
(2, 5, 'LEADER', 'APPROVED', null),
(2, 3, 'MEMBER', 'APPROVED', '参与志愿服务。'),
(3, 4, 'LEADER', 'APPROVED', null);

-- 活动类型
insert into activity_type(type_id, type_code, type_name) values
(1, 'LECTURE', '学术讲座'),
(2, 'VOLUNTEER', '志愿服务'),
(3, 'COMPETITION', '学科竞赛'),
(4, 'ARTS', '文体活动'),
(5, 'PRACTICE', '实践训练');

-- 积分规则 (每个类型一条ENABLED规则)
insert into score_rule(rule_id, type_id, base_score, normal_weight, member_weight, leader_weight, rule_desc) values
(1, 1, 1.00, 1.00, 1.20, 1.50, '讲座类活动基础分'),
(2, 2, 2.00, 1.00, 1.30, 1.60, '志愿服务按参与身份加权'),
(3, 3, 3.00, 1.00, 1.20, 1.50, '竞赛训练与获奖培育活动'),
(4, 4, 1.50, 1.00, 1.20, 1.40, '文体活动基础分'),
(5, 5, 2.50, 1.00, 1.30, 1.50, '实践训练活动基础分');

-- 活动:
-- 1: OPEN, 组织1(ACTIVE), 未来时间, 容量60已报2
-- 2: FINISHED, 组织2(ACTIVE), 已结束, 有报名
-- 3: DRAFT, 组织1(ACTIVE), 未来时间
-- 4: OPEN, 组织3(DISABLED), 用于测试禁用组织报名
-- 5: OPEN, 组织1(ACTIVE), 容量1已满且截止已过
insert into activity(activity_id, activity_name, type_id, org_id, start_time, end_time, location,
    registration_deadline, capacity, registered_count, description, requirement, rule_id, activity_status, created_by) values
(1, 'Spring Boot 实战工作坊', 5, 1, '2026-07-25 14:00:00', '2026-07-25 17:00:00', '教学楼B203',
 '2026-07-24 18:00:00', 60, 2, '后端开发实践训练。', '需携带笔记本电脑。', 5, 'OPEN', 4),
(2, '校园环保志愿服务', 2, 2, '2026-01-15 09:00:00', '2026-01-15 11:30:00', '图书馆广场',
 '2026-01-14 20:00:00', 80, 1, '校园公共区域环保宣传与清洁。', '按时签到。', 2, 'FINISHED', 5),
(3, '算法训练营预热赛', 3, 1, '2026-08-10 14:00:00', '2026-08-10 18:00:00', '实验楼C401',
 '2026-08-09 18:00:00', 40, 0, '算法训练营阶段测评。', '需现场签到。', 3, 'DRAFT', 4),
(4, '活动-禁用组织测试', 4, 3, '2026-07-20 14:00:00', '2026-07-20 17:00:00', '礼堂',
 '2026-07-19 18:00:00', 50, 0, '禁用组织中的活动。', '无', 4, 'OPEN', 4),
(5, '活动-已满员测试', 1, 1, '2026-06-10 14:00:00', '2026-06-10 18:00:00', '教室A',
 '2025-12-31 18:00:00', 1, 1, '已满员且截止已过。', '无', 1, 'OPEN', 4);

-- 报名记录
insert into registration(registration_id, activity_id, user_id, registration_status, checkin_status) values
(1, 1, 2, 'VALID', 'NOT_CHECKED'),
(2, 1, 3, 'VALID', 'NOT_CHECKED'),
(3, 2, 3, 'VALID', 'NOT_CHECKED'),
(4, 5, 2, 'VALID', 'NOT_CHECKED');

-- 积分记录: 活动2(已结束)中学生3作为组织成员已通过审核
insert into score_record(score_id, user_id, activity_id, rule_id, identity_type, base_score, identity_weight, final_score, audit_status, submitter_id, reviewer_id, reviewed_at) values
(1, 3, 2, 2, 'ORG_MEMBER', 2.00, 1.30, 2.60, 'APPROVED', 5, 1, now());
