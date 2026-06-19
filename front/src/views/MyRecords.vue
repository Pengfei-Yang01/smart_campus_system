<template>
  <AppLayout title="我的活动与积分" subtitle="查看报名记录、参与状态、积分总分和积分明细">
    <div class="metric-grid">
      <div class="metric"><span>审核通过积分</span><b>{{ approvedTotal }}</b></div>
      <div class="metric"><span>待审核记录</span><b>{{ pendingCount }}</b></div>
      <div class="metric"><span>有效报名</span><b>{{ validRegistrationCount }}</b></div>
      <div class="metric"><span>已签到活动</span><b>{{ checkedCount }}</b></div>
    </div>
    <div class="grid-2">
      <section class="panel">
        <h2 class="section-title">报名记录</h2>
        <el-table :data="registrations" height="430">
          <el-table-column prop="activity_name" label="活动名称" min-width="180" />
          <el-table-column prop="registration_status" label="报名状态" width="110" />
          <el-table-column prop="checkin_status" label="签到/参与" width="120" />
          <el-table-column prop="registered_at" label="报名时间" width="170" />
        </el-table>
      </section>
      <section class="panel">
        <h2 class="section-title">积分明细</h2>
        <el-table :data="scores" height="430">
          <el-table-column prop="activity_name" label="活动名称" min-width="160" />
          <el-table-column prop="base_score" label="基础分" width="90" />
          <el-table-column prop="identity_weight" label="权重" width="90" />
          <el-table-column prop="final_score" label="最终得分" width="100" />
          <el-table-column prop="audit_status" label="审核状态" width="110" />
          <el-table-column prop="reject_reason" label="驳回原因" min-width="120" />
        </el-table>
      </section>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

const registrations = ref([])
const scores = ref([])
const approvedTotal = computed(() => scores.value.filter((s) => s.audit_status === 'APPROVED').reduce((sum, s) => sum + Number(s.final_score || 0), 0).toFixed(2))
const pendingCount = computed(() => scores.value.filter((s) => s.audit_status === 'PENDING').length)
const validRegistrationCount = computed(() => registrations.value.filter((r) => r.registration_status === 'VALID').length)
const checkedCount = computed(() => registrations.value.filter((r) => r.checkin_status === 'CHECKED').length)

onMounted(async () => {
  registrations.value = await http.get('/me/registrations')
  scores.value = await http.get('/me/scores')
})
</script>
