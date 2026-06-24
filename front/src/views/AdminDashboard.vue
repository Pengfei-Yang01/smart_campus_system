<template>
  <!-- 管理员首页，展示全局统计和待处理事项快捷入口。
        -->
  <AppLayout title="管理员首页" subtitle="面向系统管理员的审批概览、系统统计和后台入口">
    <div class="metric-grid">
      <div class="metric"><span>学生账号</span><b>{{ stats.studentCount || 0 }}</b></div>
      <div class="metric"><span>活动总数</span><b>{{ stats.activityCount || 0 }}</b></div>
      <div class="metric"><span>组织总数</span><b>{{ stats.organizationCount || 0 }}</b></div>
      <div class="metric"><span>待审事项</span><b>{{ pendingTotal }}</b></div>
    </div>

    <div class="grid-2">
      <section class="panel">
        <h2 class="section-title">待处理审批</h2>
        <div class="detail-list">
          <div><span>负责人申请</span><b>{{ stats.pendingLeaderApplyCount || 0 }}</b></div>
          <div><span>组织成立申请</span><b>{{ stats.pendingOrgApplyCount || 0 }}</b></div>
          <div><span>事务申请</span><b>{{ stats.pendingAffairCount || 0 }}</b></div>
          <div><span>积分记录</span><b>{{ stats.pendingScoreCount || 0 }}</b></div>
          <div><span>报名中活动</span><b>{{ stats.openActivityCount || 0 }}</b></div>
        </div>
        <el-divider />
        <div class="table-actions">
          <el-button type="primary" @click="$router.push('/admin/students')">处理学生与负责人申请</el-button>
          <el-button @click="$router.push('/admin/organizations')">处理组织申请</el-button>
          <el-button @click="$router.push('/affairs')">处理事务申请</el-button>
          <el-button @click="$router.push('/admin/scores')">处理积分审核</el-button>
        </div>
      </section>

      <section class="panel">
        <h2 class="section-title">管理员职责</h2>
        <el-alert type="info" :closable="false" show-icon title="当前首页只展示管理员相关内容">
          <p>管理员不显示学生端“申请组织负责人”等表单，避免角色职责混淆。</p>
        </el-alert>
        <el-table :data="recentActivities" height="250" style="margin-top: 12px">
          <el-table-column prop="activity_name" label="近期活动" min-width="180" />
          <el-table-column prop="org_name" label="组织" width="140" />
          <el-table-column prop="activity_status" label="状态" width="100" />
        </el-table>
      </section>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

const stats = ref({})
const recentActivities = ref([])

// 仪表盘指标卡中展示的待处理总数。
const pendingTotal = computed(() =>
  Number(stats.value.pendingLeaderApplyCount || 0)
  + Number(stats.value.pendingOrgApplyCount || 0)
  + Number(stats.value.pendingScoreCount || 0)
  + Number(stats.value.pendingAffairCount || 0)
)

// 为仪表盘加载统计数据和近期活动列表。
onMounted(async () => {
  const [statRows, activityRows] = await Promise.all([
    http.get('/admin/stats'),
    http.get('/activities')
  ])
  stats.value = statRows
  recentActivities.value = activityRows.slice(0, 6)
})
</script>
