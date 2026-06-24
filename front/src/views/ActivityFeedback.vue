<template>
  <!-- 活动评价反馈页。学生提交评价，负责人和管理员管理评价。 -->
  <AppLayout title="活动评价反馈" subtitle="对已参与活动进行评分反馈，组织负责人查看并回复学生评价">
    <div class="metric-grid">
      <div class="metric"><span>我的评价</span><b>{{ myFeedbacks.length }}</b></div>
      <div class="metric"><span>可评价活动</span><b>{{ availableActivities.length }}</b></div>
      <div class="metric"><span>公开评价</span><b>{{ publicFeedbacks.length }}</b></div>
      <div class="metric"><span>待回复评价</span><b>{{ pendingReplyCount }}</b></div>
    </div>

    <section class="panel">
      <el-tabs>
        <el-tab-pane label="提交评价">
          <el-form label-position="top">
            <div class="form-grid">
              <el-form-item label="选择已完成活动">
                <el-select v-model="form.activityId" placeholder="选择活动" style="width: 100%">
                  <el-option v-for="activity in availableActivities" :key="activity.activity_id" :label="activity.activity_name" :value="activity.activity_id" />
                </el-select>
              </el-form-item>
              <el-form-item label="评分">
                <el-rate v-model="form.rating" />
              </el-form-item>
              <el-form-item label="匿名展示">
                <el-switch v-model="form.anonymous" active-text="匿名" inactive-text="实名" />
              </el-form-item>
              <el-form-item class="full" label="评价内容">
                <el-input v-model="form.content" type="textarea" :rows="4" />
              </el-form-item>
            </div>
            <div class="table-actions">
              <el-button type="primary" :disabled="!form.activityId" @click="submit">提交评价</el-button>
              <el-button @click="resetForm">重置</el-button>
            </div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="我的评价">
          <el-table :data="myFeedbacks" height="430">
            <el-table-column prop="activity_name" label="活动名称" min-width="180" />
            <el-table-column label="评分" width="150">
              <template #default="{ row }"><el-rate :model-value="Number(row.rating)" disabled /></template>
            </el-table-column>
            <el-table-column prop="content" label="评价内容" min-width="220" />
            <el-table-column prop="feedback_status" label="状态" width="110" />
            <el-table-column prop="reply_content" label="回复" min-width="180" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="公开评价">
          <div class="table-actions">
            <el-select v-model="selectedActivityId" placeholder="选择活动" filterable style="width: 320px" @change="loadPublicFeedbacks">
              <el-option v-for="activity in activities" :key="activity.activity_id" :label="activity.activity_name" :value="activity.activity_id" />
            </el-select>
            <el-button :icon="Refresh" @click="loadPublicFeedbacks">刷新</el-button>
          </div>
          <el-table :data="publicFeedbacks" height="430">
            <el-table-column prop="display_name" label="评价人" width="120" />
            <el-table-column label="评分" width="150">
              <template #default="{ row }"><el-rate :model-value="Number(row.rating)" disabled /></template>
            </el-table-column>
            <el-table-column prop="content" label="评价内容" min-width="240" />
            <el-table-column prop="reply_content" label="组织回复" min-width="220" />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="canManage" label="评价管理">
          <div class="table-actions">
            <el-select v-model="manageStatus" placeholder="展示状态" clearable style="width: 160px" @change="loadManageFeedbacks">
              <el-option label="VISIBLE" value="VISIBLE" />
              <el-option label="HIDDEN" value="HIDDEN" />
            </el-select>
            <el-button :icon="Refresh" @click="loadManageFeedbacks">刷新</el-button>
          </div>
          <el-table :data="managedFeedbacks" height="500">
            <el-table-column prop="activity_name" label="活动名称" min-width="180" />
            <el-table-column prop="user_name" label="评价人" width="110" />
            <el-table-column label="评分" width="150">
              <template #default="{ row }"><el-rate :model-value="Number(row.rating)" disabled /></template>
            </el-table-column>
            <el-table-column prop="content" label="评价内容" min-width="220" />
            <el-table-column prop="feedback_status" label="状态" width="110" />
            <el-table-column prop="reply_content" label="回复" min-width="160" />
            <el-table-column label="操作" width="230">
              <template #default="{ row }">
                <el-button size="small" @click="reply(row)">回复</el-button>
                <el-button size="small" @click="setStatus(row, row.feedback_status === 'VISIBLE' ? 'HIDDEN' : 'VISIBLE')">
                  {{ row.feedback_status === 'VISIBLE' ? '隐藏' : '恢复' }}
                </el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const registrations = ref([])
const myFeedbacks = ref([])
const activities = ref([])
const publicFeedbacks = ref([])
const managedFeedbacks = ref([])
const selectedActivityId = ref(null)
const manageStatus = ref('')
const form = reactive(defaultForm())

const canManage = computed(() => auth.isAdmin || auth.isLeader)
const reviewedActivityIds = computed(() => new Set(myFeedbacks.value.map((row) => row.activity_id)))
const availableActivities = computed(() => registrations.value.filter((row) =>
  row.registration_status === 'VALID'
  && row.checkin_status === 'CHECKED'
  && row.activity_status === 'FINISHED'
  && !reviewedActivityIds.value.has(row.activity_id)
))
const pendingReplyCount = computed(() => managedFeedbacks.value.filter((row) => !row.reply_content).length)

onMounted(loadAll)

function defaultForm() {
  return { activityId: null, rating: 5, content: '', anonymous: false }
}

async function loadAll() {
  const [registrationRows, feedbackRows, activityRows] = await Promise.all([
    http.get('/me/registrations'),
    http.get('/feedbacks/mine'),
    http.get('/activities')
  ])
  registrations.value = registrationRows
  myFeedbacks.value = feedbackRows
  activities.value = activityRows
  selectedActivityId.value = activities.value.find((row) => row.activity_status === 'FINISHED')?.activity_id || activities.value[0]?.activity_id || null
  resetForm()
  await Promise.all([loadPublicFeedbacks(), canManage.value ? loadManageFeedbacks() : Promise.resolve()])
}

function resetForm() {
  Object.assign(form, defaultForm(), { activityId: availableActivities.value[0]?.activity_id || null })
}

async function submit() {
  await http.post(`/activities/${form.activityId}/feedbacks`, {
    rating: form.rating,
    content: form.content,
    anonymous: form.anonymous
  })
  ElMessage.success('评价已提交')
  const [feedbackRows, publicRows] = await Promise.all([
    http.get('/feedbacks/mine'),
    selectedActivityId.value ? http.get(`/activities/${selectedActivityId.value}/feedbacks`) : Promise.resolve([])
  ])
  myFeedbacks.value = feedbackRows
  publicFeedbacks.value = publicRows
  resetForm()
}

async function loadPublicFeedbacks() {
  publicFeedbacks.value = selectedActivityId.value ? await http.get(`/activities/${selectedActivityId.value}/feedbacks`) : []
}

async function loadManageFeedbacks() {
  if (!canManage.value) return
  managedFeedbacks.value = await http.get('/feedbacks/manage', { params: { status: manageStatus.value || undefined } })
}

async function reply(row) {
  const { value } = await ElMessageBox.prompt('请输入回复内容', '回复活动评价', { inputValue: row.reply_content || '' })
  await http.patch(`/feedbacks/${row.feedback_id}/reply`, { replyContent: value })
  ElMessage.success('评价回复已保存')
  await loadManageFeedbacks()
}

async function setStatus(row, status) {
  await http.patch(`/feedbacks/${row.feedback_id}/status`, { status })
  ElMessage.success('评价状态已更新')
  await Promise.all([loadManageFeedbacks(), loadPublicFeedbacks()])
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').replace(/\.\d+$/, '') : ''
}
</script>
