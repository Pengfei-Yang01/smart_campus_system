<template>
  <AppLayout title="AI 助手" subtitle="结合活动、报名、组织和积分数据回答校园事务问题">
    <section class="panel ai-page">
      <div class="ai-history">
        <div v-if="messages.length === 0" class="ai-empty">
          <el-empty description="还没有问答记录，可以先问问最近有哪些活动适合报名。" />
        </div>
        <div v-for="message in messages" :key="message.id" class="ai-message" :class="message.role">
          <div class="ai-message-role">{{ message.role === 'user' ? '我' : 'AI 助手' }}</div>
          <div class="ai-message-content">{{ message.content }}</div>
          <div v-if="message.meta" class="ai-message-meta">{{ message.meta }}</div>
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
        <el-button type="primary" :loading="sending" :disabled="!question.trim()" @click="send">发送</el-button>
      </div>
    </section>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

const question = ref('')
const sending = ref(false)
const messages = ref([])

onMounted(loadRecords)

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
  try {
    const result = await http.post('/ai/chat', { question: text }, { timeout: 35000 })
    messages.value.push({
      id: `local-a-${Date.now()}`,
      role: 'assistant',
      content: result.answer,
      meta: `${result.modelName} | ${result.costMs || 0}ms`
    })
  } finally {
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

.ai-input-row {
  display: grid;
  grid-template-columns: 1fr 96px;
  gap: 12px;
  align-items: stretch;
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
