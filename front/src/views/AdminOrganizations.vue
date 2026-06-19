<template>
  <AppLayout title="组织与审核管理" subtitle="审批组织成立申请，管理组织状态，保留历史活动、成员和积分记录">
    <section class="panel">
      <el-tabs>
        <el-tab-pane label="组织成立申请">
          <el-table :data="applies" height="430">
            <el-table-column prop="org_name" label="组织名称" min-width="180" />
            <el-table-column prop="org_type" label="类型" width="120" />
            <el-table-column prop="applicant_name" label="申请人" width="110" />
            <el-table-column prop="apply_reason" label="成立理由" min-width="220" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="reject_reason" label="驳回原因" min-width="130" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.status !== 'PENDING'" @click="auditApply(row, 'APPROVED')">通过</el-button>
                <el-button size="small" :disabled="row.status !== 'PENDING'" @click="rejectApply(row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="组织状态管理">
          <el-table :data="orgs" height="430">
            <el-table-column prop="org_name" label="组织名称" min-width="180" />
            <el-table-column prop="org_type" label="类型" width="120" />
            <el-table-column prop="principal_name" label="负责人" width="120" />
            <el-table-column prop="org_status" label="状态" width="120" />
            <el-table-column label="操作" width="240">
              <template #default="{ row }">
                <el-button size="small" @click="setStatus(row, 'ACTIVE')">启用</el-button>
                <el-button size="small" @click="setStatus(row, 'DISABLED')">停用</el-button>
                <el-button size="small" @click="$router.push(`/organizations/${row.org_id}`)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

const applies = ref([])
const orgs = ref([])
onMounted(load)

async function load() {
  const [applyRows, orgRows] = await Promise.all([http.get('/admin/org-applies'), http.get('/admin/organizations')])
  applies.value = applyRows
  orgs.value = orgRows
}

async function auditApply(row, status, rejectReason = '') {
  await http.patch(`/admin/org-applies/${row.org_apply_id}`, { status, rejectReason })
  ElMessage.success('组织申请已处理')
  await load()
}

async function rejectApply(row) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回组织申请')
  await auditApply(row, 'REJECTED', value)
}

async function setStatus(row, orgStatus) {
  await http.patch(`/admin/organizations/${row.org_id}/status`, { orgStatus })
  ElMessage.success('组织状态已更新')
  await load()
}
</script>
