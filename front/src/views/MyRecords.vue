<template>
  <!-- 个人活动与积分页面，由学生、组织负责人和
       管理员共用，因为每个用户都可能拥有个人记录。 -->
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
// 规范导入顺序：Vue内置API → 公共组件 → 接口请求工具
import { computed, onMounted, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

// 常量抽离魔法字符串，仅优化维护性，不改变匹配规则
const AUDIT_STATUS_APPROVED = 'APPROVED'
const AUDIT_STATUS_PENDING = 'PENDING'
const REG_STATUS_VALID = 'VALID'
const CHECKIN_STATUS_CHECKED = 'CHECKED'

// 响应式数据源（语义化命名）
const userRegistrationList = ref([])
const userScoreList = ref([])

/**
 * 计算属性：统计审核通过的积分总金额，保留2位小数
 */
const calcApprovedTotal = computed(() => {
  return userScoreList.value
    .filter(item => item.audit_status === AUDIT_STATUS_APPROVED)
    .reduce((total, item) => total + Number(item.final_score || 0), 0)
    .toFixed(2)
})

/**
 * 计算属性：统计待审核的积分记录条数
 */
const calcPendingCount = computed(() => {
  return userScoreList.value.filter(item => item.audit_status === AUDIT_STATUS_PENDING).length
})

/**
 * 计算属性：统计有效报名（未取消）的活动数量
 */
const calcValidRegistrationCount = computed(() => {
  return userRegistrationList.value.filter(item => item.registration_status === REG_STATUS_VALID).length
})

/**
 * 计算属性：统计已签到的活动报名数量
 */
const calcCheckedCount = computed(() => {
  return userRegistrationList.value.filter(item => item.checkin_status === CHECKIN_STATUS_CHECKED).length
})

/**
 * 页面初始化：加载当前用户报名记录、积分明细
 */
onMounted(async () => {
  userRegistrationList.value = await http.get('/me/registrations')
  userScoreList.value = await http.get('/me/scores')
})

const registrations = userRegistrationList
const scores = userScoreList
const approvedTotal = calcApprovedTotal
const pendingCount = calcPendingCount
const validRegistrationCount = calcValidRegistrationCount
const checkedCount = calcCheckedCount
</script>