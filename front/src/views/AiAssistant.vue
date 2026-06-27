<template>
  <AppLayout title="AI 助手" subtitle="结合活动、报名、组织和积分数据回答校园事务问题">
    <section class="panel ai-page">
      <div v-if="messages.length > 0" class="ai-toolbar">
        <div class="ai-toolbar-left">
          <el-button v-if="!selectMode" size="small" @click="enterSelectMode">
            批量管理
          </el-button>
          <template v-else>
            <el-button size="small" @click="exitSelectMode">退出管理</el-button>
            <span v-if="selectedQaIds.length" class="ai-selected-count">
              已选 {{ selectedQaIds.length }} 条
            </span>
          </template>
        </div>
        <div class="ai-toolbar-right">
          <template v-if="selectMode">
            <el-checkbox
              :model-value="isAllSelected"
              :indeterminate="isIndeterminate"
              @change="toggleAll"
              size="small"
            >
              全选
            </el-checkbox>
            <el-button
              type="danger"
              size="small"
              :disabled="!selectedQaIds.length"
              @click="batchDelete"
            >
              删除选中
            </el-button>
          </template>
          <el-popconfirm
            v-else
            title="确定清空所有问答记录？此操作不可恢复。"
            @confirm="clearAll"
          >
            <template #reference>
              <el-button size="small" type="danger" plain>清空记录</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>

      <div class="ai-history">
        <div v-if="messages.length === 0" class="ai-empty">
          <el-empty description="还没有问答记录，可以先问问最近有哪些活动适合报名。" />
        </div>
        <div
          v-for="message in messages"
          :key="message.id"
          class="ai-message"
          :class="[message.role, { 'is-select-mode': selectMode }]"
        >
          <!-- 多选模式的勾选框 -->
          <div v-if="selectMode && getQaId(message)" class="ai-select-wrapper" @click.stop>
            <el-checkbox
              :model-value="selectedQaIds.includes(getQaId(message))"
              @change="() => toggleSelect(getQaId(message))"
            />
          </div>

          <!-- 悬停删除按钮 -->
          <el-popconfirm
            v-if="!selectMode && getQaId(message)"
            title="删除这条问答记录？"
            @confirm="deleteSingle(getQaId(message))"
            placement="left"
          >
            <template #reference>
              <el-icon class="ai-delete-icon"><Delete /></el-icon>
            </template>
          </el-popconfirm>

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
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="sending" :disabled="!question.trim()" @click="send">
          {{ sending ? `${waitingSeconds}s` : '发送' }}
        </el-button>
      </div>
    </section>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { marked } from 'marked'
import { Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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

/* ----- 多选删除状态 ----- */
const selectMode = ref(false)
const selectedQaIds = ref([])

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
  const localId = `local-q-${Date.now()}`
  messages.value.push({ id: localId, role: 'user', content: text })
  question.value = ''
  sending.value = true
  startWaitingTimer()
  try {
    const result = await http.post('/ai/chat', { question: text }, { timeout: 35000 })
    // 用后端返回的真实 qa_id 更新本地消息 ID
    const idx = messages.value.findIndex((m) => m.id === localId)
    if (idx >= 0) {
      messages.value[idx].id = `q-${result.qaId}`
    }
    messages.value.push({
      id: `a-${result.qaId}`,
      role: 'assistant',
      content: result.answer,
      meta: `${result.modelName} | ${result.costMs || 0}ms`
    })
  } finally {
    stopWaitingTimer()
    sending.value = false
  }
}

/* ========== 删除逻辑 ========== */

/** 从消息 ID 中提取 qa_id（"q-123" → 123，"local-…" → null） */
function getQaId(message) {
  if (!message || !message.id) return null
  if (message.id.startsWith('local-')) return null
  return parseInt(message.id.replace(/^[qa]-/, ''), 10)
}

/** 单条删除 */
async function deleteSingle(qaId) {
  try {
    await http.delete(`/ai/record/${qaId}`)
    messages.value = messages.value.filter((m) => getQaId(m) !== qaId)
    ElMessage.success('已删除')
  } catch {
    // http 拦截器已弹出错误提示
  }
}

/** 清空所有 */
async function clearAll() {
  try {
    await ElMessageBox.confirm('确定要清空所有问答记录吗？此操作不可恢复。', '确认清空', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await http.delete('/ai/records')
    messages.value = []
    ElMessage.success('已清空所有记录')
  } catch {
    // 用户取消或 http 错误——无需额外操作
  }
}

/* ========== 多选模式逻辑 ========== */

function enterSelectMode() {
  selectMode.value = true
  selectedQaIds.value = []
}

function exitSelectMode() {
  selectMode.value = false
  selectedQaIds.value = []
}

function toggleSelect(qaId) {
  const idx = selectedQaIds.value.indexOf(qaId)
  if (idx >= 0) {
    selectedQaIds.value.splice(idx, 1)
  } else {
    selectedQaIds.value.push(qaId)
  }
}

/** 所有可选的 qa_id（去重） */
const allSelectableIds = computed(() => {
  const ids = new Set()
  for (const m of messages.value) {
    const id = getQaId(m)
    if (id !== null) ids.add(id)
  }
  return [...ids]
})

const isAllSelected = computed(() => {
  return (
    allSelectableIds.value.length > 0 &&
    selectedQaIds.value.length === allSelectableIds.value.length
  )
})

const isIndeterminate = computed(() => {
  return (
    selectedQaIds.value.length > 0 &&
    selectedQaIds.value.length < allSelectableIds.value.length
  )
})

function toggleAll(val) {
  selectedQaIds.value = val ? [...allSelectableIds.value] : []
}

/** 批量删除选中 */
async function batchDelete() {
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedQaIds.value.length} 条问答记录？此操作不可恢复。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await http.post('/ai/records/batch-delete', [...selectedQaIds.value])
    const ids = new Set(selectedQaIds.value)
    messages.value = messages.value.filter((m) => !ids.has(getQaId(m)))
    ElMessage.success(`已删除 ${ids.size} 条记录`)
    selectedQaIds.value = []
    selectMode.value = false
  } catch {
    // 用户取消或 http 错误
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

/* ---- 工具栏 ---- */
.ai-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 2px;
}

.ai-toolbar-left,
.ai-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-selected-count {
  font-size: 13px;
  color: #64748b;
}

/* ---- 历史消息区域 ---- */
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
  position: relative;
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

/* ---- 悬停删除图标 ---- */
.ai-delete-icon {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 15px;
  color: #94a3b8;
  cursor: pointer;
  z-index: 1;
  opacity: 0;
  transition: opacity 0.2s, color 0.2s;
}

.ai-message:hover .ai-delete-icon {
  opacity: 1;
}

.ai-delete-icon:hover {
  color: #f56c6c;
}

/* ---- 多选模式 ---- */
.ai-message.is-select-mode {
  padding-left: 40px;
}

.ai-select-wrapper {
  position: absolute;
  left: 10px;
  top: 10px;
  z-index: 1;
}

/* ---- Markdown ---- */
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

/* ---- 输入区域 ---- */
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
