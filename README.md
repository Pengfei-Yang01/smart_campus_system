# 智慧校园综测服务与活动管理系统

本项目包含 Spring Boot 后端、Vue3 + Element Plus 前端、MySQL 初始化脚本。AI 问答模块按当前要求暂不实现，仅保留菜单占位和 `ai_qa_record` 表。

## 技术栈

- 后端：Spring Boot 3.3、JdbcTemplate、轻量 Token 认证
- 前端：Vue3、Pinia、Vue Router、Element Plus、Axios
- 数据库：MySQL 8.0

## 初始化数据库

```powershell
& 'D:\mysql\mysql-8.0.42-winx64\bin\mysql.exe' -uroot -p你的密码 --execute="source D:/smart_campus_system/scripts/init_mysql.sql"
```

脚本会创建并重置 `smart_campus` 数据库，内置测试数据。

测试账号密码均为 `123456`：

- `admin`：管理员
- `leader1`：组织负责人
- `student1`：普通学生

## 启动后端

如果 MySQL 用户不是 `root`，或密码不为空，使用环境变量覆盖：

```powershell
$env:DB_USERNAME='root'
$env:DB_PASSWORD='你的密码'
& 'D:\Idea\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd' spring-boot:run
```

后端地址：`http://localhost:8080`

如果你的 MySQL 设置了密码，请在启动前配置 `DB_PASSWORD`：

```powershell
$env:DB_PASSWORD='你的MySQL密码'
& 'D:\Idea\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd' spring-boot:run
```

## 启动前端

```powershell
cd D:\smart_campus_system\front
& 'D:\web\nodejs\npm.cmd' install
& 'D:\web\nodejs\npm.cmd' run dev
```

前端地址：`http://localhost:5173`

## 已实现页面与模块

- 登录、注册、角色识别、Token 拦截
- 学生首页：近期活动、报名数、待审积分、负责人申请和进度展示
- 活动中心页面：活动查询、类型/状态/关键词筛选、分页展示、剩余名额、基础分值
- 活动详情页面：活动简介、活动要求、报名限制、加分权重、报名/取消报名
- 组织中心页面：组织列表、组织关系、组织详情、组织活动、申请加入组织
- 我的活动与积分页面：报名记录、签到状态、个人积分总分、积分明细、驳回原因
- AI 助手页面：按要求暂不接入模型，保留页面占位和数据库表
- 组织负责人管理页面：组织成立申请、申请进度、组织信息维护、成员审批、活动发布、活动编辑、状态维护、报名名单、签到、录分
- 管理员学生管理页面：学生查询、账号启停、信息修改、负责人申请审批、学生详情追溯
- 管理员组织与审核页面：组织成立申请审批、组织状态启停、组织详情查看
- 管理员积分审核页面：积分审核、驳回原因、积分规则维护

## 数据库脚本

完整脚本位于：

```text
D:\smart_campus_system\scripts\init_mysql.sql
```

更详细的跨电脑运行步骤见：

```text
D:\smart_campus_system\运行部署说明.md
```
