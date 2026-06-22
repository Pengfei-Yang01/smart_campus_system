# AI 助手大模型接入设计

## 背景

当前 `front/src/views/AiAssistant.vue` 是占位页面，后端尚无 AI 接口。数据库初始化脚本已经预留 `ai_qa_record` 表，可保存用户问题、模型回答、模型名、token 用量、耗时和调用时间。

本次目标是在不绑定单一供应商的前提下，为 AI 助手接入可配置的大模型 API，并结合当前登录用户的校园业务数据生成回答。

## 方案选择

采用“OpenAI 兼容接口 + 轻量业务上下文增强”方案。

后端通过环境变量配置大模型服务地址、API Key 和模型名，按 OpenAI Chat Completions 兼容格式调用：

```text
POST {AI_API_BASE_URL}/chat/completions
```

这样可以接入 OpenAI、DeepSeek、通义兼容网关、硅基流动等支持 OpenAI 兼容协议的平台。前端和业务代码不依赖具体供应商。

## 后端配置

在 `application.yml` 的 `app.ai` 下新增配置：

```yaml
app:
  ai:
    enabled: ${AI_ENABLED:false}
    api-base-url: ${AI_API_BASE_URL:https://api.openai.com/v1}
    api-key: ${AI_API_KEY:}
    model: ${AI_MODEL:gpt-4o-mini}
    timeout-seconds: ${AI_TIMEOUT_SECONDS:30}
```

默认关闭 AI 服务，避免未配置 Key 时启动失败或误调用外部接口。运行时由本机环境变量启用：

```powershell
$env:AI_ENABLED='true'
$env:AI_API_BASE_URL='https://api.openai.com/v1'
$env:AI_API_KEY='你的API Key'
$env:AI_MODEL='gpt-4o-mini'
```

## 后端接口

新增 `AiController`，路径统一放在 `/api/ai` 下，沿用现有认证拦截器，所有接口都需要登录。

### POST /api/ai/chat

请求：

```json
{
  "question": "最近有哪些活动适合我报名？"
}
```

校验：

- `question` 不能为空。
- `question` 去除首尾空白后长度不超过 1000 字。
- `AI_ENABLED=true` 且 `AI_API_KEY` 已配置后才允许调用模型。

响应：

```json
{
  "answer": "可以优先关注...",
  "modelName": "gpt-4o-mini",
  "costMs": 1260,
  "promptTokens": 512,
  "completionTokens": 180
}
```

成功调用后写入 `ai_qa_record`。

### GET /api/ai/records

返回当前登录用户最近 20 条问答记录，按 `called_at desc` 排序。仅返回当前用户自己的记录，管理员也不默认查看他人 AI 记录，避免隐私泄露。

## 业务上下文

后端在调用模型前查询一小段当前用户可见的业务摘要，并拼入 system 消息。

基础上下文：

- 当前用户姓名、用户名、角色列表。
- 最近开放活动 5 条：活动名、类型、组织、时间、地点、剩余名额。
- 当前用户最近报名记录 5 条：活动名、报名状态、签到状态、活动状态。
- 当前用户积分概览：已审核总分、待审核记录数、最近积分记录 5 条。

负责人上下文：

- 当前用户负责的组织列表。
- 负责人待处理的成员加入申请数量。
- 当前用户组织下近期活动数量。

管理员上下文：

- 用户数、活动数、组织数、待审核负责人申请数、待审核组织申请数、待审核积分数。

上下文只由后端查询生成，不允许模型直接生成 SQL 或访问数据库。所有查询都基于现有登录用户身份限制，模型只能看到摘要数据。

## 模型提示词

发送给模型的消息结构：

```json
[
  {
    "role": "system",
    "content": "你是智慧校园综合服务与活动管理系统的 AI 助手..."
  },
  {
    "role": "user",
    "content": "【系统业务上下文】...\n\n【用户问题】最近有哪些活动适合我报名？"
  }
]
```

系统提示词约束：

- 使用中文回答。
- 优先基于提供的系统上下文回答。
- 如果上下文不足，明确说明需要用户到对应页面查看或补充信息。
- 不编造不存在的活动、报名、积分或审核状态。
- 不输出数据库结构、SQL、密钥、token 或内部实现细节。
- 回答尽量简洁，可用项目符号。

## 大模型客户端

新增一个小型服务类，例如 `AiClient` 或 `AiService`：

- 负责读取配置。
- 负责构造 OpenAI 兼容请求。
- 使用 Java 21 自带 `java.net.http.HttpClient`，不新增额外依赖。
- 设置超时。
- 解析 `choices[0].message.content`。
- 如果 `usage.prompt_tokens` 和 `usage.completion_tokens` 存在，则返回给控制器保存。

控制器负责业务校验、上下文查询、落库和统一响应；客户端只负责外部模型调用。

## 前端设计

改造 `AiAssistant.vue`：

- 使用现有 `AppLayout`。
- 页面主体为聊天界面：历史消息区、输入框、发送按钮。
- 页面加载时调用 `GET /api/ai/records` 展示最近问答。
- 发送问题时调用 `POST /api/ai/chat`。
- 发送中禁用按钮并显示 loading。
- AI 未启用或 Key 未配置时，展示后端返回的错误提示。

前端先实现普通非流式响应。当前项目的 `http.js` 基于 axios 统一封装响应，非流式接入成本更低，也更贴合现有代码。

## 错误处理

- AI 服务未启用：返回“AI 服务未启用，请配置 AI_ENABLED=true”。
- API Key 未配置：返回“AI API Key 未配置”。
- 外部接口超时：返回“AI 服务响应超时，请稍后重试”。
- 外部接口返回错误：返回“AI 服务暂时不可用，请稍后重试”。
- 响应格式异常：返回“AI 服务返回格式异常”。
- 用户问题过长：返回“问题不能超过 1000 字”。

失败调用不写入 `ai_qa_record`，避免把错误页或异常文本当成有效问答历史。

## 数据落库

复用现有 `ai_qa_record` 表：

```text
user_id: 当前登录用户 ID
question: 用户问题
answer: 模型回答
model_name: 配置中的模型名
prompt_tokens: 模型返回则保存，否则 0
completion_tokens: 模型返回则保存，否则 0
cost_ms: 外部调用耗时
called_at: 数据库默认当前时间
```

当前表结构足够支持第一版功能，不新增迁移脚本。

## 安全与隐私

- API Key 只通过环境变量配置，不写入代码、前端或数据库。
- 前端永远不直接调用模型供应商，所有请求走后端。
- 历史记录只展示当前用户自己的问答。
- 业务上下文只包含摘要信息，不包含密码哈希、token、邮箱等不必要字段。
- 模型不可直接执行数据库查询。

## 测试与验证

后端验证：

- AI 未启用时，`POST /api/ai/chat` 返回友好错误。
- Key 缺失时，返回友好错误。
- 问题为空或过长时，返回校验错误。
- 使用模拟 OpenAI 兼容响应验证答案解析和 `ai_qa_record` 落库。
- `GET /api/ai/records` 只返回当前用户记录。

前端验证：

- AI 页面能加载历史记录。
- 发送问题后出现 loading，成功后追加用户消息和 AI 回答。
- 失败时显示错误提示，输入框恢复可用。
- 未登录时仍由现有路由守卫跳转登录页。

集成验证：

- 配置真实 `AI_API_BASE_URL`、`AI_API_KEY`、`AI_MODEL` 后，使用 `admin / 123456` 登录并在 `/ai` 提问。
- 验证模型回答包含校园业务上下文，例如近期活动、当前用户角色或积分摘要。
- 验证数据库 `ai_qa_record` 写入成功。

## 非目标

第一版不实现流式输出、多轮上下文记忆、文件上传、知识库检索、模型工具调用、管理员查看全站 AI 日志或用量计费统计。这些能力可以在基础问答稳定后再扩展。
