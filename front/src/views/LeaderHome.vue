<template>
  <!-- 组织负责人首页，只展示负责人相关概览数据，
       并刻意避免显示学生专属申请表单。 -->
  <AppLayout title="负责人首页" subtitle="面向组织负责人的组织、成员、活动和录分概览">
    <div class="metric-grid">
      <div class="metric"><span>负责组织</span><b>{{ managedOrgs.length }}</b></div>
      <div class="metric"><span>组织活动</span><b>{{ managedActivities.length }}</b></div>
      <div class="metric"><span>待审批成员</span><b>{{ pendingMembers }}</b></div>
      <div class="metric"><span>待审积分</span><b>{{ pendingScores.length }}</b></div>
    </div>

    <div class="grid-2">
      <section class="panel">
        <h2 class="section-title">我的负责组织</h2>
        <el-table :data="managedOrgs" height="300">
          <el-table-column prop="org_name" label="组织名称" min-width="180" />
          <el-table-column prop="org_type" label="类型" width="110" />
          <el-table-column prop="org_status" label="状态" width="110" />
        </el-table>
        <div class="table-actions" style="margin-top: 12px">
          <el-button type="primary" @click="$router.push('/leader')">进入负责人管理</el-button>
          <el-button @click="$router.push('/activities')">浏览活动</el-button>
        </div>
      </section>

      <section class="panel">
        <h2 class="section-title">组织申请进度</h2>
        <el-alert type="info" :closable="false" show-icon title="当前首页只展示负责人相关内容">
          <p>负责人不显示学生端“申请组织负责人”表单，避免角色职责混淆。</p>
        </el-alert>
        <el-table :data="myApplies" height="250" style="margin-top: 12px">
          <el-table-column prop="org_name" label="申请组织" min-width="160" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="reject_reason" label="驳回原因" min-width="140" />
        </el-table>
      </section>
    </div>

    <section class="panel" style="margin-top: 16px">
      <h2 class="section-title">我负责组织的活动</h2>
      <el-table :data="managedActivities" height="320">
        <el-table-column prop="activity_name" label="活动名称" min-width="180" />
        <el-table-column prop="org_name" label="组织" width="150" />
        <el-table-column prop="start_time" label="开始时间" width="170" />
        <el-table-column prop="registered_count" label="报名人数" width="100" />
        <el-table-column prop="activity_status" label="状态" width="110" />
      </el-table>
    </section>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const orgs = ref([])
const activities = ref([])
const myApplies = ref([])
const pendingScores = ref([])
const memberRows = ref([])

// 当前负责人名下的组织。
const managedOrgs = computed(() => orgs.value.filter((org) => org.principal_user_id === auth.user?.userId))

// 使用集合加快活动筛选，避免重复遍历数组。
const managedOrgIds = computed(() => new Set(managedOrgs.value.map((org) => org.org_id)))

// 当前负责人管理的组织所发布的活动。
const managedActivities = computed(() => activities.value.filter((activity) => managedOrgIds.value.has(activity.org_id)))

// 所有负责组织中的待审核成员申请数。
const pendingMembers = computed(() => memberRows.value.filter((member) => member.join_status === 'PENDING').length)

// 加载负责人首页数据，然后获取负责组织的成员列表。
onMounted(async () => {
  const [orgRows, activityRows, applyRows, scoreRows] = await Promise.all([
    http.get('/organizations'),
    http.get('/activities'),
    http.get('/organizations/applies/mine'),
    http.get('/scores', { params: { status: 'PENDING' } })
  ])
  orgs.value = orgRows
  activities.value = activityRows
  myApplies.value = applyRows
  pendingScores.value = scoreRows
  const memberLists = await Promise.all(managedOrgs.value.map((org) => http.get(`/organizations/${org.org_id}/members`)))
  memberRows.value = memberLists.flat()
})
</script>
