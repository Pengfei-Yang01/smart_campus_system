<template>
  <AppLayout title="AI 助手" subtitle="结合活动、报名、组织和积分数据回答校园事务问题">
    <section class="panel ai-page">
      <div class="ai-history">
        <div v-if="messages.length === 0" class="ai-empty">
          <el-empty description="还没有问答记录，可以先问问最近有哪些活动适合报名。" />
        </div>
        <div v-for="message in messages" :key="message.id" class="ai-message" :class="message.role">
          <div class="ai-message-role">{{ message.role === 'user' ? '我' : 'AI 助手' }}</div>
          <div v-if="message.role === 'user'" class="ai-message-content">{{ message.content }}</div>
          <div v-else class="ai-message-content markdown-body" v-html="renderMarkdown(message.content)"></div>
          <div v-if="message.meta" class="ai-message-meta">{{ message.meta }}</div>
        </div>
      </div>

      <div v-if="sending" class="ai-message assistant waiting-message">
        <div class="ai-message-role">AI 助手</div>
        <div class="ai-message-content">
          <span class="waiting-dot">●</span>
          思考中...（{{ waitingSeconds }}s）
        </div>
      </div>

      <div class="ai-input-row">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="例如：最近有哪些活动适合我报名？我的积分情况怎么样？"
          @keydown.ctrl.enter.prevent="send"
        />
        <el-button type="primary" :loading="sending" :disabled="!question.trim()" @click="send">
          {{ sending ? `${waitingSeconds}s` : '发送' }}
        </el-button>
      </div>
    </section>
  </AppLayout>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { marked } from 'marked'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

marked.setOptions({ breaks: true, gfm: true })

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}

const question = ref('')
const sending = ref(false)
const messages = ref([])
const waitingSeconds = ref(0)
let waitingTimer = null

onMounted(loadRecords)

onBeforeUnmount(() => {
  clearInterval(waitingTimer)
})

function startWaitingTimer() {
  waitingSeconds.value = 0
  waitingTimer = setInterval(() => {
    waitingSeconds.value++
  }, 1000)
}

function stopWaitingTimer() {
  clearInterval(waitingTimer)
  waitingTimer = null
  waitingSeconds.value = 0
}

async function loadRecords() {
  const records = await http.get('/ai/records')
  messages.value = [...records].reverse().flatMap((record) => [
    {
      id: `q-${record.qa_id}`,
      role: 'user',
      content: record.question
    },
    {
      id: `a-${record.qa_id}`,
      role: 'assistant',
      content: record.answer,
      meta: `${record.model_name} | ${record.cost_ms || 0}ms`
    }
  ])
}

async function send() {
  const text = question.value.trim()
  if (!text || sending.value) return
  messages.value.push({ id: `local-q-${Date.now()}`, role: 'user', content: text })
  question.value = ''
  sending.value = true
  startWaitingTimer()
  try {
    const result = await http.post('/ai/chat', { question: text }, { timeout: 35000 })
    messages.value.push({
      id: `local-a-${Date.now()}`,
      role: 'assistant',
      content: result.answer,
      meta: `${result.modelName} | ${result.costMs || 0}ms`
    })
  } finally {
    stopWaitingTimer()
    sending.value = false
  }
}
</script>

<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 620px;
}

.ai-history {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  padding-right: 4px;
}

.ai-empty {
  margin: auto;
}

.ai-message {
  max-width: 78%;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  white-space: pre-wrap;
  line-height: 1.6;
}

.ai-message.user {
  align-self: flex-end;
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.ai-message.assistant {
  align-self: flex-start;
}

.ai-message-role {
  margin-bottom: 6px;
  font-size: 12px;
  color: #64748b;
}

.ai-message-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.markdown-body {
  white-space: normal;
  line-height: 1.7;
  font-size: 14px;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  margin: 14px 0 6px;
  font-weight: 600;
  line-height: 1.4;
}

.markdown-body h1 { font-size: 1.3em; }
.markdown-body h2 { font-size: 1.2em; }
.markdown-body h3 { font-size: 1.1em; }

.markdown-body p {
  margin: 6px 0;
}

.markdown-body ul,
.markdown-body ol {
  margin: 6px 0;
  padding-left: 22px;
}

.markdown-body li {
  margin: 3px 0;
}

.markdown-body code {
  padding: 2px 6px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #d63384;
  font-size: 0.9em;
}

.markdown-body pre {
  margin: 10px 0;
  padding: 12px 14px;
  border-radius: 6px;
  background: #1e293b;
  overflow-x: auto;
}

.markdown-body pre code {
  padding: 0;
  background: none;
  color: #e2e8f0;
  font-size: 13px;
  line-height: 1.5;
}

.markdown-body blockquote {
  margin: 8px 0;
  padding: 4px 14px;
  border-left: 4px solid #409eff;
  color: #64748b;
  background: #f8fafc;
  border-radius: 0 4px 4px 0;
}

.markdown-body table {
  margin: 10px 0;
  border-collapse: collapse;
  font-size: 13px;
  width: 100%;
}

.markdown-body th,
.markdown-body td {
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  text-align: left;
}

.markdown-body th {
  background: #f1f5f9;
  font-weight: 600;
}

.markdown-body hr {
  margin: 14px 0;
  border: none;
  border-top: 1px solid #e2e8f0;
}

.markdown-body strong {
  font-weight: 600;
}

.markdown-body a {
  color: #409eff;
  text-decoration: none;
}

.markdown-body a:hover {
  text-decoration: underline;
}

.ai-input-row {
  display: grid;
  grid-template-columns: 1fr 96px;
  gap: 12px;
  align-items: stretch;
}

.waiting-message {
  align-self: flex-start;
  opacity: 0.8;
}

.waiting-dot {
  display: inline-block;
  color: #409eff;
  font-size: 10px;
  margin-right: 4px;
  animation: waiting-pulse 1.2s ease-in-out infinite;
}

@keyframes waiting-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

@media (max-width: 720px) {
  .ai-message {
    max-width: 100%;
  }

  .ai-input-row {
    grid-template-columns: 1fr;
  }
}
</style>
