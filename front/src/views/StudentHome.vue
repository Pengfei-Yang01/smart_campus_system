<template>
  <AppLayout title="学生首页" subtitle="近期活动、个人积分概览、负责人申请进度和常用入口">
    <div class="metric-grid">
      <div class="metric"><span>有效报名</span><b>{{ data.registrationCount || 0 }}</b></div>
      <div class="metric"><span>待审积分</span><b>{{ data.pendingScoreCount || 0 }}</b></div>
      <div class="metric"><span>已通过积分</span><b>{{ data.approvedScore?.total || 0 }}</b></div>
      <div class="metric"><span>负责人申请</span><b>{{ data.leaderApply?.status || '未申请' }}</b></div>
    </div>

    <div class="grid-2">
      <section class="panel">
        <div class="table-actions">
          <h2 class="section-title">近期活动</h2>
          <el-button text type="primary" @click="$router.push('/activities')">进入活动中心</el-button>
        </div>
        <el-table :data="data.recentActivities || []" height="360" @row-click="row => $router.push(`/activities/${row.activity_id}`)">
          <el-table-column prop="activity_name" label="活动名称" min-width="180" />
          <el-table-column prop="type_name" label="类型" width="110" />
          <el-table-column prop="org_name" label="主办组织" width="150" />
          <el-table-column prop="start_time" label="活动时间" width="170" />
          <el-table-column prop="activity_status" label="状态" width="100" />
        </el-table>
      </section>

      <section class="panel">
        <h2 class="section-title">申请成为组织负责人</h2>
        <el-alert v-if="data.leaderApply?.status" :title="leaderStatusText" :type="leaderAlertType" show-icon :closable="false" />
        <el-form label-position="top" style="margin-top: 12px">
          <el-form-item label="申请理由">
            <el-input v-model="form.applyReason" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item label="联系方式">
            <el-input v-model="form.contact" />
          </el-form-item>
          <el-form-item label="拟负责方向或相关经历">
            <el-input v-model="form.experience" type="textarea" :rows="3" />
          </el-form-item>
          <el-button type="primary" :icon="Promotion" :disabled="data.leaderApply?.status === 'PENDING'" @click="submit">提交申请</el-button>
          <el-button v-if="auth.isLeader || auth.isAdmin" @click="$router.push('/leader')">进入负责人管理</el-button>
        </el-form>
      </section>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const data = ref({})
const form = reactive({ applyReason: '', contact: '', experience: '' })

const leaderStatusText = computed(() => {
  const row = data.value.leaderApply
  if (!row?.status) return ''
  if (row.status === 'REJECTED') return `申请已驳回：${row.reject_reason || '未填写原因'}`
  if (row.status === 'APPROVED') return '申请已通过，已开放组织负责人管理入口'
  return '申请正在等待管理员审批'
})
const leaderAlertType = computed(() => data.value.leaderApply?.status === 'APPROVED' ? 'success' : data.value.leaderApply?.status === 'REJECTED' ? 'warning' : 'info')

onMounted(load)

async function load() {
  data.value = await http.get('/dashboard/student')
}

async function submit() {
  await http.post('/students/leader-apply', form)
  ElMessage.success('申请已提交')
  Object.assign(form, { applyReason: '', contact: '', experience: '' })
  await load()
}
</script>
