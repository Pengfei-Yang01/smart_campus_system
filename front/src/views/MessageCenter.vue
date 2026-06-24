<template>
  <!-- 消息通知中心。所有角色查看个人消息，管理员可以额外发布公告。 -->
  <AppLayout title="消息通知中心" subtitle="接收审批结果、系统公告、评价回复和待处理提醒">
    <div class="metric-grid">
      <div class="metric"><span>全部消息</span><b>{{ messages.length }}</b></div>
      <div class="metric"><span>未读消息</span><b>{{ unreadCount }}</b></div>
      <div class="metric"><span>公告通知</span><b>{{ noticeCount }}</b></div>
      <div class="metric"><span>重要公告</span><b>{{ importantCount }}</b></div>
    </div>

    <section class="panel">
      <el-tabs>
        <el-tab-pane label="我的消息">
          <div class="table-actions">
            <el-switch v-model="onlyUnread" active-text="只看未读" @change="loadMessages" />
            <el-button :icon="Refresh" @click="loadMessages">刷新</el-button>
            <el-button type="primary" @click="readAll">全部标为已读</el-button>
          </div>
          <el-table :data="messages" height="500">
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.read_at ? 'info' : 'danger'">{{ row.read_at ? '已读' : '未读' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="category" label="类型" width="100" />
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column prop="content" label="内容" min-width="260" />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button size="small" :disabled="!!row.read_at" @click="readOne(row)">标为已读</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="auth.isAdmin" label="发布公告">
          <el-form label-position="top">
            <div class="form-grid">
              <el-form-item label="公告标题">
                <el-input v-model="noticeForm.title" />
              </el-form-item>
              <el-form-item label="目标角色">
                <el-select v-model="noticeForm.targetRole" style="width: 100%">
                  <el-option label="全部用户" value="ALL" />
                  <el-option label="普通学生" value="STUDENT" />
                  <el-option label="组织负责人" value="ORG_LEADER" />
                  <el-option label="系统管理员" value="ADMIN" />
                </el-select>
              </el-form-item>
              <el-form-item label="优先级">
                <el-select v-model="noticeForm.priority" style="width: 100%">
                  <el-option label="普通" value="NORMAL" />
                  <el-option label="重要" value="IMPORTANT" />
                </el-select>
              </el-form-item>
              <el-form-item class="full" label="公告内容">
                <el-input v-model="noticeForm.content" type="textarea" :rows="5" />
              </el-form-item>
            </div>
            <div class="table-actions">
              <el-button type="primary" :icon="Promotion" @click="publish">发布公告</el-button>
              <el-button @click="resetNotice">重置</el-button>
            </div>
          </el-form>

          <el-divider />
          <el-table :data="notices" height="300">
            <el-table-column prop="title" label="公告标题" min-width="180" />
            <el-table-column prop="target_role" label="目标角色" width="120" />
            <el-table-column prop="priority" label="优先级" width="100" />
            <el-table-column prop="receiver_count" label="接收人数" width="100" />
            <el-table-column prop="notice_status" label="状态" width="110" />
            <el-table-column prop="published_at" label="发布时间" width="170" />
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.notice_status === 'DISABLED'" @click="disableNotice(row)">停用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Refresh } from '@element-plus/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const messages = ref([])
const notices = ref([])
const onlyUnread = ref(false)
const unreadCount = ref(0)
const noticeForm = reactive(defaultNotice())

const noticeCount = computed(() => messages.value.filter((row) => row.category === 'NOTICE').length)
const importantCount = computed(() => messages.value.filter((row) => row.priority === 'IMPORTANT').length)

onMounted(loadAll)

function defaultNotice() {
  return { title: '', content: '', targetRole: 'ALL', priority: 'NORMAL' }
}

async function loadAll() {
  await Promise.all([loadMessages(), loadNotices()])
}

async function loadMessages() {
  messages.value = await http.get('/messages', { params: { unread: onlyUnread.value || undefined } })
  const data = await http.get('/messages/unread-count')
  unreadCount.value = data.count || 0
}

async function loadNotices() {
  if (auth.isAdmin) notices.value = await http.get('/admin/notices')
}

async function readOne(row) {
  await http.patch(`/messages/${row.message_id}/read`)
  await loadMessages()
}

async function readAll() {
  await http.patch('/messages/read-all')
  ElMessage.success('已全部标为已读')
  await loadMessages()
}

async function publish() {
  await http.post('/admin/notices', noticeForm)
  ElMessage.success('公告已发布')
  resetNotice()
  await loadAll()
}

function resetNotice() {
  Object.assign(noticeForm, defaultNotice())
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').replace(/\.\d+$/, '') : ''
}

async function disableNotice(row) {
  await http.patch(`/admin/notices/${row.notice_id}/disable`)
  ElMessage.success('公告已停用')
  await loadNotices()
}
</script>
