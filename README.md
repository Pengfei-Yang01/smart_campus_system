# Smart Campus Activity Management System

智慧校园综测服务与活动管理系统是一个前后端分离的校园活动管理项目，覆盖学生报名、组织管理、活动发布、成员审批、签到、综测积分录入与审核、学生事务申请、消息通知、活动评价反馈等流程。系统按角色划分学生端、组织负责人端和管理员端，并根据主角色进入不同首页。

> AI 问答模块已接入 OpenAI 兼容接口，默认关闭。配置 `AI_ENABLED=true` 和 `AI_API_KEY` 后可启用模型问答；未启用时历史记录仍可查看，提问会返回受控提示。

## Features

- 用户登录、注册、Token 鉴权和角色识别
- 普通学生首页、活动中心、活动详情、组织中心、组织详情、我的活动与积分
- 组织负责人首页、组织信息维护、组织成立申请、成员加入审批、活动发布与状态维护、报名名单、签到、录分
- 管理员首页、学生管理、组织成立审批、组织状态管理、积分审核、积分规则维护
- 学生事务申请：学生申请桌椅、宣传位、物资，组织负责人可额外申请教室和场地，管理员审批
- 消息通知中心：审批结果、系统公告、评价回复和待处理提醒按角色进入个人收件箱
- 活动评价反馈：已签到参与者评价活动，组织负责人管理和回复评价，管理员可全局管理
- AI 助手：基于活动、报名、组织和积分上下文回答校园事务问题，并保存问答记录
- MySQL 初始化脚本，包含建表语句和演示数据
- 按主角色隔离首页与导航，避免管理员、负责人、学生权限混淆

## Tech Stack

### Backend

- Java 17
- Spring Boot 3.3
- Spring JDBC / JdbcTemplate
- MySQL Connector/J
- Maven

### Frontend

- Vue 3
- Vue Router
- Pinia
- Element Plus
- Axios
- Vite

### Database

- MySQL 8.0+

## Project Structure

```text
smart_campus_system
├─ backend/                 Spring Boot backend
├─ front/                   Vue 3 frontend
├─ scripts/
│  └─ init_mysql.sql        Database schema and seed data
├─ 运行部署说明.md            Detailed Chinese setup guide
└─ README.md
```

## Prerequisites

Install these tools before running the project:

- JDK 17
- Maven 3.8+
- MySQL 8.0+
- Node.js 18+
- npm

Check versions:

```bash
java -version
mvn -version
mysql --version
node -v
npm -v
```

## Database Setup

Create and seed the database by running the SQL script.

```bash
mysql --default-character-set=utf8mb4 -uroot -p --execute="source scripts/init_mysql.sql"
```

If you run the command outside the project root, use an absolute path:

```bash
mysql --default-character-set=utf8mb4 -uroot -p --execute="source /path/to/smart_campus_system/scripts/init_mysql.sql"
```

Notes:

- The script creates a database named `smart_campus`.
- Running the script again will reset the demo database.
- `--default-character-set=utf8mb4` is recommended because the seed data contains Chinese text.

## Backend Setup

Enter the backend directory:

```bash
cd backend
```

Set database connection environment variables if your MySQL settings are not the defaults.

Linux/macOS:

```bash
export DB_USERNAME=root
export DB_PASSWORD=your_mysql_password
```

Windows PowerShell:

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
```

Optional AI configuration:

```bash
AI_ENABLED=true
AI_API_KEY=your_api_key
AI_API_BASE_URL=https://api.openai.com/v1
AI_MODEL=gpt-4o-mini
```

The AI module uses an OpenAI-compatible `/chat/completions` endpoint. Keep `AI_ENABLED=false` or omit it if you only want to run the normal campus management features.

Run the backend:

```bash
mvn spring-boot:run
```

Backend default URL:

```text
http://localhost:8080
```

Health check:

```text
http://localhost:8080/api/health
```

Expected response:

```json
{"code":0,"message":"ok","data":{"status":"UP"}}
```

## Frontend Setup

Enter the frontend directory:

```bash
cd front
```

Install dependencies:

```bash
npm install
```

Run the development server:

```bash
npm run dev
```

Frontend default URL:

```text
http://localhost:5173
```

The Vite development server proxies `/api` requests to:

```text
http://localhost:8080
```

Make sure the backend is running before using the frontend.

## Demo Accounts

The seed data includes these accounts. All passwords are:

```text
123456
```

| Username | Role | Description |
| --- | --- | --- |
| `admin` | Admin | Student management, organization approval, score audit |
| `leader1` | Organization leader | Manage organizations, activities, members, check-in and scores |
| `leader2` | Organization leader | Another organization leader account |
| `student1` | Student | Browse activities, register, join organizations, view scores |
| `student2` | Student | Another student account |

## Role-Based Home Pages

After login, the system redirects users by primary role:

| Primary role | Home page | Description |
| --- | --- | --- |
| `ADMIN` | `/admin` | Admin dashboard |
| `ORG_LEADER` | `/leader-home` | Organization leader dashboard |
| `STUDENT` | `/home` | Student dashboard |

Only students see the "apply to become organization leader" form. Admins and organization leaders have separate dashboards and navigation menus.

## Main Pages

### Student

- `/home`: student dashboard, recent activities, score overview, leader application status
- `/activities`: activity list, filters and pagination
- `/activities/:id`: activity detail, registration and cancellation
- `/organizations`: organization list
- `/organizations/:id`: organization detail and join application
- `/affairs`: student affairs application and approval progress
- `/messages`: personal message center
- `/feedbacks`: activity feedback submission and browsing
- `/mine`: personal registrations and score records
- `/ai`: AI assistant chat and history records

### Organization Leader

- `/leader-home`: leader dashboard
- `/leader`: organization application, organization maintenance, member approval, activity management, check-in and score submission
- `/activities`: activity browsing
- `/organizations`: organization browsing
- `/affairs`: affairs application, including classroom and venue resources for managed organizations
- `/messages`: personal message center
- `/feedbacks`: activity feedback management and reply
- `/mine`: personal registrations and score records
- `/ai`: AI assistant chat and history records

### Admin

- `/admin`: admin dashboard and pending tasks
- `/admin/students`: student list, account enable/disable, student detail, leader application approval
- `/admin/organizations`: organization application approval and organization status management
- `/admin/scores`: score audit and score rule maintenance
- `/activities`: activity overview
- `/organizations`: organization overview
- `/affairs`: affairs approval
- `/messages`: message center and notice publishing
- `/feedbacks`: global activity feedback management
- `/mine`: personal registrations and score records
- `/ai`: AI assistant chat and history records

## Build Commands

Backend build:

```bash
cd backend
mvn -DskipTests package
```

Frontend build:

```bash
cd front
npm run build
```

## Common Issues

### MySQL connection failed

Check:

- MySQL service is running
- `smart_campus` database has been initialized
- `DB_USERNAME` and `DB_PASSWORD` are correct
- MySQL port is accessible

### SQL import shows Chinese encoding errors

Run the import with:

```bash
mysql --default-character-set=utf8mb4 -uroot -p --execute="source scripts/init_mysql.sql"
```

### Frontend cannot call backend APIs

Check:

- Backend is running on `http://localhost:8080`
- Frontend is running from the `front` directory
- `front/vite.config.js` contains the `/api` proxy configuration

### Port is already in use

Default ports:

- Backend: `8080`
- Frontend: `5173`

Stop the process using the port or change the port in the corresponding configuration.

## More Detailed Guide

For a step-by-step Chinese setup guide, see:

```text
运行部署说明.md
```
