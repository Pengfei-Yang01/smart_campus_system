<template>
  <!-- 活动详情页，展示活动基本信息、积分信息和
       当前用户可执行的报名操作。 -->
  <AppLayout title="活动详情" subtitle="查看活动要求、报名限制、加分说明和当前报名状态">
    <section class="panel">
      <div class="table-actions">
        <el-button @click="$router.back()">返回</el-button>
        <el-button type="primary" :disabled="!canRegister" @click="register">报名</el-button>
        <el-button :disabled="!detail.registered" @click="cancel">取消报名</el-button>
      </div>
      <h2 class="section-title">{{ detail.activity_name }}</h2>
      <div class="detail-list">
        <div><span>活动类型</span><b>{{ detail.type_name }}</b></div>
        <div><span>主办组织</span><b>{{ detail.org_name }}</b></div>
        <div><span>时间</span><b>{{ detail.start_time }} 至 {{ detail.end_time }}</b></div>
        <div><span>地点</span><b>{{ detail.location }}</b></div>
        <div><span>报名截止</span><b>{{ detail.registration_deadline }}</b></div>
        <div><span>名额</span><b>{{ detail.registered_count }} / {{ detail.capacity }}</b></div>
        <div><span>状态</span><b>{{ detail.activity_status }}</b></div>
        <div><span>当前报名状态</span><b>{{ detail.registered ? '已报名' : '未报名' }}</b></div>
        <div><span>基础分值</span><b>{{ detail.base_score }}</b></div>
        <div><span>身份权重</span><b>普通 {{ detail.normal_weight }}，成员 {{ detail.member_weight }}，负责人 {{ detail.leader_weight }}</b></div>
      </div>
      <el-divider />
      <h3>活动简介</h3>
      <p>{{ detail.description || '暂无简介' }}</p>
      <h3>活动要求与报名限制</h3>
      <p>{{ detail.requirement || '暂无要求' }}</p>
    </section>

    <section class="panel" style="margin-top: 16px">
      <div class="table-actions">
        <h2 class="section-title">活动评价</h2>
        <el-button text type="primary" @click="$router.push('/feedbacks')">进入评价反馈</el-button>
      </div>
      <el-table :data="feedbacks" height="260">
        <el-table-column prop="display_name" label="评价人" width="120" />
        <el-table-column label="评分" width="150">
          <template #default="{ row }"><el-rate :model-value="Number(row.rating)" disabled /></template>
        </el-table-column>
        <el-table-column prop="content" label="评价内容" min-width="220" />
        <el-table-column prop="reply_content" label="组织回复" min-width="220" />
      </el-table>
    </section>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

const route = useRoute()
const detail = ref({})
const feedbacks = ref([])

// 只有活动开放、当前用户尚未报名，且
// 仍有剩余名额时才允许报名。
const canRegister = computed(() => detail.value.org_status === 'ACTIVE' && detail.value.activity_status === 'OPEN' && !detail.value.registered && Number(detail.value.registered_count || 0) < Number(detail.value.capacity || 0))

onMounted(load)

// 根据路由编号加载详情。
async function load() {
  const [activityDetail, feedbackRows] = await Promise.all([
    http.get(`/activities/${route.params.id}`),
    http.get(`/activities/${route.params.id}/feedbacks`)
  ])
  detail.value = activityDetail
  feedbacks.value = feedbackRows
}

// 为当前用户报名，然后重新加载详情以更新按钮状态和人数。
async function register() {
  await http.post(`/activities/${route.params.id}/register`)
  ElMessage.success('报名成功')
  await load()
}

// 取消当前用户的有效报名，然后重新加载详情。
async function cancel() {
  await http.delete(`/activities/${route.params.id}/register`)
  ElMessage.success('已取消报名')
  await load()
}
</script>
