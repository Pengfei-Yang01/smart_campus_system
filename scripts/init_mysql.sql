create database if not exists smart_campus default character set utf8mb4 collate utf8mb4_unicode_ci;
use smart_campus;

set foreign_key_checks = 0;
drop table if exists user_message;
drop table if exists notice;
drop table if exists activity_feedback;
drop table if exists affair_application;
drop table if exists campus_resource;
drop table if exists affair_type;
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

create table affair_type (
  type_id bigint primary key auto_increment,
  type_code varchar(32) not null unique,
  type_name varchar(80) not null,
  applicant_scope enum('ALL','ORG_LEADER') not null default 'ALL',
  requires_resource tinyint(1) not null default 0,
  enabled_status enum('ENABLED','DISABLED') not null default 'ENABLED',
  sort_order int not null default 100,
  description varchar(300)
) engine=InnoDB default charset=utf8mb4;

create table campus_resource (
  resource_id bigint primary key auto_increment,
  type_id bigint not null,
  resource_name varchar(120) not null,
  resource_location varchar(150),
  capacity int,
  resource_status enum('ENABLED','DISABLED') not null default 'ENABLED',
  description varchar(300),
  index idx_campus_resource_type_status(type_id, resource_status),
  constraint fk_campus_resource_type foreign key (type_id) references affair_type(type_id)
) engine=InnoDB default charset=utf8mb4;

create table affair_application (
  affair_id bigint primary key auto_increment,
  applicant_id bigint not null,
  applicant_role enum('STUDENT','ORG_LEADER') not null,
  org_id bigint,
  type_id bigint not null,
  resource_id bigint,
  title varchar(120) not null,
  apply_reason varchar(600) not null,
  expected_start datetime not null,
  expected_end datetime not null,
  quantity int not null default 1 check (quantity > 0),
  contact varchar(100),
  status enum('PENDING','APPROVED','REJECTED','CANCELLED') not null default 'PENDING',
  reviewer_id bigint,
  reject_reason varchar(300),
  review_remark varchar(500),
  applied_at datetime not null default current_timestamp,
  reviewed_at datetime,
  check (expected_end > expected_start),
  index idx_affair_status_time(status, expected_start),
  index idx_affair_applicant_status(applicant_id, status),
  constraint fk_affair_applicant foreign key (applicant_id) references user_account(user_id),
  constraint fk_affair_org foreign key (org_id) references organization(org_id),
  constraint fk_affair_type foreign key (type_id) references affair_type(type_id),
  constraint fk_affair_resource foreign key (resource_id) references campus_resource(resource_id),
  constraint fk_affair_reviewer foreign key (reviewer_id) references user_account(user_id)
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

create table activity_feedback (
  feedback_id bigint primary key auto_increment,
  activity_id bigint not null,
  user_id bigint not null,
  rating int not null check (rating between 1 and 5),
  content varchar(800) not null,
  anonymous tinyint(1) not null default 0,
  feedback_status enum('VISIBLE','HIDDEN') not null default 'VISIBLE',
  reply_content varchar(800),
  replied_by bigint,
  replied_at datetime,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_feedback_activity_user(activity_id, user_id),
  index idx_feedback_activity_status(activity_id, feedback_status),
  constraint fk_feedback_activity foreign key (activity_id) references activity(activity_id),
  constraint fk_feedback_user foreign key (user_id) references user_account(user_id),
  constraint fk_feedback_replier foreign key (replied_by) references user_account(user_id)
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

create table notice (
  notice_id bigint primary key auto_increment,
  title varchar(120) not null,
  content varchar(1200) not null,
  target_role enum('ALL','STUDENT','ORG_LEADER','ADMIN') not null default 'ALL',
  priority enum('NORMAL','IMPORTANT') not null default 'NORMAL',
  publisher_id bigint not null,
  notice_status enum('PUBLISHED','DISABLED') not null default 'PUBLISHED',
  published_at datetime not null default current_timestamp,
  index idx_notice_target_status(target_role, notice_status),
  constraint fk_notice_publisher foreign key (publisher_id) references user_account(user_id)
) engine=InnoDB default charset=utf8mb4;

create table user_message (
  message_id bigint primary key auto_increment,
  recipient_id bigint not null,
  title varchar(120) not null,
  content varchar(1200) not null,
  category enum('SYSTEM','NOTICE','AUDIT','AFFAIR','FEEDBACK') not null default 'SYSTEM',
  source_type varchar(50),
  source_id bigint,
  source_notice_id bigint,
  read_at datetime,
  created_at datetime not null default current_timestamp,
  index idx_user_message_recipient_read(recipient_id, read_at, created_at),
  constraint fk_user_message_recipient foreign key (recipient_id) references user_account(user_id),
  constraint fk_user_message_notice foreign key (source_notice_id) references notice(notice_id)
) engine=InnoDB default charset=utf8mb4;

insert into role(role_code, role_name, description) values
('STUDENT', '普通学生', '浏览活动、报名、申请加入组织、查看个人积分'),
('ORG_LEADER', '组织负责人', '管理本人负责组织、发布活动、审批成员、录入积分'),
('ADMIN', '系统管理员', '管理学生、组织审批、负责人审批、积分审核和系统统计');

insert into user_account(student_no, username, password_hash, real_name, phone, email) values
('A0001', 'admin', sha2('123456', 256), '系统管理员', '13800000000', 'admin@campus.local'),
('S2023001', 'student1', sha2('123456', 256), '林小北', '13800000001', 'student1@campus.local'),
('S2023002', 'student2', sha2('123456', 256), '周明远', '13800000002', 'student2@campus.local'),
('S2023003', 'leader1', sha2('123456', 256), '陈思源', '13800000003', 'leader1@campus.local'),
('S2023004', 'leader2', sha2('123456', 256), '许安然', '13800000004', 'leader2@campus.local');

insert into student_profile(user_id, college, major, class_name, grade) values
(1, '软件学院', '软件工程', '软工2311104', '2023'),
(2, '软件学院', '软件工程', '软工2311104', '2023'),
(3, '计算机学院', '人工智能', '智能2302', '2023'),
(4, '软件学院', '软件工程', '软工2210', '2022'),
(5, '经济管理学院', '信息管理', '信管2201', '2022');

insert into user_role(user_id, role_id)
select 1, role_id from role where role_code in ('STUDENT','ADMIN');
insert into user_role(user_id, role_id)
select user_id, (select role_id from role where role_code='STUDENT') from user_account where user_id in (2,3,4,5);
insert into user_role(user_id, role_id)
select user_id, (select role_id from role where role_code='ORG_LEADER') from user_account where user_id in (4,5);

insert into leader_apply(user_id, apply_reason, contact, experience, status, reviewer_id, reviewed_at) values
(4, '长期参与社团活动，希望负责活动组织与学分认定。', 'leader1@campus.local', '曾任学习委员与社团活动负责人。', 'APPROVED', 1, now()),
(5, '具备志愿活动组织经验，申请成为组织负责人。', 'leader2@campus.local', '负责过校级志愿服务项目。', 'APPROVED', 1, now()),
(2, '希望创建编程交流小组。', 'student1@campus.local', '参加过多次算法竞赛。', 'PENDING', null, null);

insert into organization(org_name, org_type, description, contact, principal_user_id, org_status) values
('软件创新协会', 'CLUB', '面向全校学生开展技术分享、编程训练和项目实践。', 'softclub@campus.local', 4, 'ACTIVE'),
('青年志愿者服务队', 'UNIVERSITY', '组织公益服务、社区实践和校园志愿活动。', 'volunteer@campus.local', 5, 'ACTIVE'),
('软件学院学生会', 'COLLEGE', '负责学院文体活动、讲座与学生服务。', 'studentunion@campus.local', 4, 'ACTIVE');

insert into organization_member(org_id, user_id, member_role, join_status, apply_reason) values
(1, 4, 'LEADER', 'APPROVED', null),
(1, 2, 'MEMBER', 'APPROVED', '希望参与项目实践。'),
(1, 3, 'MEMBER', 'PENDING', '想加入技术分享活动。'),
(2, 5, 'LEADER', 'APPROVED', null),
(2, 3, 'MEMBER', 'APPROVED', '参与志愿服务。'),
(3, 4, 'LEADER', 'APPROVED', null);

insert into organization_apply(applicant_id, org_name, org_type, description, apply_reason, contact, status) values
(4, 'AI 学习小组', 'CLUB', '围绕机器学习与大模型实践开展交流。', '补充校园 AI 学习活动供给。', 'ai-club@campus.local', 'PENDING');

insert into affair_type(type_code, type_name, applicant_scope, requires_resource, sort_order, description) values
('DESK_CHAIR', '桌椅借用', 'ALL', 0, 10, '学生或负责人申请临时桌椅，用于班级、社团或个人学习服务场景。'),
('POSTER_SPACE', '海报张贴位', 'ALL', 1, 20, '申请校内宣传栏或活动海报张贴位置。'),
('EQUIPMENT', '活动物资借用', 'ALL', 0, 30, '申请音箱、展架、插线板等常规活动物资。'),
('CLASSROOM', '教室借用', 'ORG_LEADER', 1, 40, '组织负责人为组织活动申请教室。'),
('AUDITORIUM', '报告厅/场地借用', 'ORG_LEADER', 1, 50, '组织负责人申请报告厅、礼堂等大型活动场地。');

insert into campus_resource(type_id, resource_name, resource_location, capacity, description) values
(2, '一号宣传栏', '教学楼A座一层大厅', 12, '适合张贴社团招新、讲座预告等海报。'),
(2, '图书馆入口展板区', '图书馆南门', 8, '人流量较大，需保持版面整洁。'),
(3, '便携音箱套装', '团委物资室', 6, '包含音箱、无线麦克风和基础连接线。'),
(3, '移动展架', '大学生活动中心一层仓库', 20, '适合活动签到、成果展示和海报陈列。'),
(3, '插线板套装', '后勤服务中心', 15, '适合培训、路演等临时用电场景。'),
(4, '教学楼B203', '教学楼B座二层', 60, '多媒体教室，适合培训和讲座。'),
(4, '实验楼C401', '实验楼C座四层', 40, '机房教室，适合编程训练。'),
(5, '大学生活动中心报告厅', '大学生活动中心二层', 180, '适合校级讲座、路演和大型宣讲。');

insert into affair_application(applicant_id, applicant_role, org_id, type_id, resource_id, title, apply_reason,
    expected_start, expected_end, quantity, contact, status, reviewer_id, review_remark, reviewed_at) values
(2, 'STUDENT', null, 1, null, '班级学习分享桌椅借用', '班级开展学习经验分享会，需要临时借用桌椅。', '2026-07-02 18:00:00', '2026-07-02 21:00:00', 8, 'student1@campus.local', 'PENDING', null, null, null),
(3, 'STUDENT', null, 2, 1, '志愿服务海报张贴申请', '为校园环保志愿服务活动张贴宣传海报。', '2026-07-04 09:00:00', '2026-07-08 18:00:00', 2, 'student2@campus.local', 'APPROVED', 1, '同意在指定展板张贴，请活动结束后及时撤下。', now()),
(4, 'ORG_LEADER', 1, 4, 6, '软件创新协会培训教室申请', '用于开展 Spring Boot 项目实战培训。', '2026-07-06 14:00:00', '2026-07-06 17:00:00', 1, 'leader1@campus.local', 'APPROVED', 1, '请提前 20 分钟到物业办公室领取钥匙。', now()),
(5, 'ORG_LEADER', 2, 5, 8, '志愿者服务队报告厅申请', '用于举办暑期志愿服务项目说明会。', '2026-07-15 19:00:00', '2026-07-15 21:00:00', 1, 'leader2@campus.local', 'PENDING', null, null, null);

insert into affair_application(applicant_id, applicant_role, org_id, type_id, resource_id, title, apply_reason,
    expected_start, expected_end, quantity, contact, status, reviewer_id, reject_reason, reviewed_at) values
(2, 'STUDENT', null, 3, null, '活动物资临时借用', '个人学习小组临时需要借用较多物资，但未说明保管人。', '2026-07-09 13:00:00', '2026-07-09 18:00:00', 3, 'student1@campus.local', 'REJECTED', 1, '请补充物资清单和保管负责人后重新提交。', now());

insert into activity_type(type_code, type_name) values
('LECTURE', '学术讲座'),
('VOLUNTEER', '志愿服务'),
('COMPETITION', '学科竞赛'),
('ARTS', '文体活动'),
('PRACTICE', '实践训练');

insert into score_rule(type_id, base_score, normal_weight, member_weight, leader_weight, rule_desc) values
(1, 1.00, 1.00, 1.20, 1.50, '讲座类活动基础分'),
(2, 2.00, 1.00, 1.30, 1.60, '志愿服务按参与身份加权'),
(3, 3.00, 1.00, 1.20, 1.50, '竞赛训练与获奖培育活动'),
(4, 1.50, 1.00, 1.20, 1.40, '文体活动基础分'),
(5, 2.50, 1.00, 1.30, 1.50, '实践训练活动基础分');

insert into activity(activity_name, type_id, org_id, start_time, end_time, location, registration_deadline, capacity, registered_count, description, requirement, base_score_override, rule_id, activity_status, created_by) values
('Spring Boot 项目实战工作坊', 5, 1, '2026-06-25 14:00:00', '2026-06-25 17:00:00', '教学楼 B203', '2026-06-24 18:00:00', 60, 2, '围绕校园系统后端开发进行实践训练。', '需携带笔记本电脑。', 2.50, 5, 'OPEN', 4),
('校园环保志愿服务', 2, 2, '2026-06-28 09:00:00', '2026-06-28 11:30:00', '图书馆广场', '2026-06-27 20:00:00', 80, 1, '开展校园公共区域环保宣传与清洁服务。', '按时签到，服从现场安排。', 2.00, 2, 'OPEN', 5),
('软件工程职业规划讲座', 1, 3, '2026-06-30 19:00:00', '2026-06-30 21:00:00', '大学生活动中心', '2026-06-29 18:00:00', 120, 0, '邀请企业导师分享软件工程职业发展路径。', '报名后准时参加。', 1.00, 1, 'OPEN', 4),
('算法训练营阶段赛', 3, 1, '2026-06-10 14:00:00', '2026-06-10 18:00:00', '实验楼 C401', '2026-06-09 18:00:00', 40, 2, '面向算法训练营成员开展阶段测评。', '需完成报名并现场签到。', 3.00, 3, 'FINISHED', 4),
('校园歌手赛志愿保障', 4, 2, '2026-06-12 18:00:00', '2026-06-12 22:00:00', '礼堂', '2026-06-11 18:00:00', 30, 1, '协助完成校园歌手赛现场引导与秩序维护。', '志愿者需提前半小时到场。', 1.50, 4, 'FINISHED', 5);

insert into registration(activity_id, user_id, registration_status, checkin_status) values
(1, 2, 'VALID', 'NOT_CHECKED'),
(1, 3, 'VALID', 'NOT_CHECKED'),
(2, 3, 'VALID', 'NOT_CHECKED'),
(4, 2, 'VALID', 'CHECKED'),
(4, 3, 'VALID', 'CHECKED'),
(5, 3, 'VALID', 'CHECKED');

insert into activity_feedback(activity_id, user_id, rating, content, anonymous, reply_content, replied_by, replied_at) values
(4, 2, 5, '训练内容很扎实，希望后续增加题解复盘环节。', 0, '感谢建议，下一期会加入复盘安排。', 4, now()),
(4, 3, 4, '活动节奏紧凑，签到流程顺畅，建议增加更多实操时间。', 1, null, null, null),
(5, 3, 4, '现场秩序维护安排清晰，签到流程还可以再快一点。', 1, null, null, null);

insert into score_record(user_id, activity_id, rule_id, identity_type, base_score, identity_weight, final_score, audit_status, submitter_id, reviewer_id, reviewed_at) values
(2, 4, 3, 'ORG_MEMBER', 3.00, 1.20, 3.60, 'APPROVED', 4, 1, now()),
(3, 4, 3, 'NORMAL', 3.00, 1.00, 3.00, 'PENDING', 4, null, null),
(3, 5, 4, 'ORG_MEMBER', 1.50, 1.20, 1.80, 'APPROVED', 5, 1, now());

insert into ai_qa_record(user_id, question, answer, model_name, cost_ms) values
(2, '最近有哪些活动可以报名？', 'AI 问答模块暂未启用，请在活动中心查看可报名活动。', 'not-enabled', 0);

insert into notice(title, content, target_role, priority, publisher_id) values
('校园活动管理系统新增功能上线', '学生事务申请、消息通知中心和活动评价反馈模块已开放使用，请根据角色进入对应页面处理。', 'ALL', 'IMPORTANT', 1),
('事务申请审批规范提醒', '提交教室和场地申请时，请完整填写使用时间、组织名称、活动用途和联系方式。', 'ORG_LEADER', 'NORMAL', 1),
('活动评价反馈功能开放', '已签到且已结束的活动可以在评价反馈页面提交评分和文字建议，组织负责人会及时查看回复。', 'STUDENT', 'NORMAL', 1);

insert into user_message(recipient_id, title, content, category, source_type, source_id, source_notice_id)
select user_id, '校园活动管理系统新增功能上线', '学生事务申请、消息通知中心和活动评价反馈模块已开放使用，请根据角色进入对应页面处理。', 'NOTICE', 'NOTICE', 1, 1
from user_account
where account_status='ENABLED';

insert into user_message(recipient_id, title, content, category, source_type, source_id, source_notice_id) values
(2, '你的事务申请待审批', '班级学习分享桌椅借用申请已提交，请等待管理员审批。', 'AFFAIR', 'AFFAIR_APPLICATION', 1, null),
(3, '海报张贴申请已通过', '志愿服务海报张贴申请已通过，请按审批说明使用宣传栏。', 'AUDIT', 'AFFAIR_APPLICATION', 2, null),
(4, '事务申请已通过', '软件创新协会培训教室申请已通过，请按审批说明使用教室。', 'AUDIT', 'AFFAIR_APPLICATION', 3, null),
(5, '事务申请待审批', '志愿者服务队报告厅申请已提交，请等待管理员审批。', 'AFFAIR', 'AFFAIR_APPLICATION', 4, null),
(2, '活动物资申请已驳回', '活动物资临时借用申请被驳回：请补充物资清单和保管负责人后重新提交。', 'AUDIT', 'AFFAIR_APPLICATION', 5, null),
(4, '收到新的活动评价', '学生对算法训练营阶段赛提交了评价，请及时查看反馈。', 'FEEDBACK', 'ACTIVITY_FEEDBACK', 1, null),
(4, '事务申请审批规范提醒', '提交教室和场地申请时，请完整填写使用时间、组织名称、活动用途和联系方式。', 'NOTICE', 'NOTICE', 2, 2),
(5, '事务申请审批规范提醒', '提交教室和场地申请时，请完整填写使用时间、组织名称、活动用途和联系方式。', 'NOTICE', 'NOTICE', 2, 2),
(2, '活动评价反馈功能开放', '已签到且已结束的活动可以在评价反馈页面提交评分和文字建议，组织负责人会及时查看回复。', 'NOTICE', 'NOTICE', 3, 3),
(3, '活动评价反馈功能开放', '已签到且已结束的活动可以在评价反馈页面提交评分和文字建议，组织负责人会及时查看回复。', 'NOTICE', 'NOTICE', 3, 3);
