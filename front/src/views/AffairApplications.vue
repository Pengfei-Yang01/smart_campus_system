<template>
  <!-- 学生事务申请页。学生和负责人提交申请，管理员在同一入口完成审批。 -->
  <AppLayout title="学生事务申请" subtitle="申请桌椅、物资、宣传位、教室和场地资源，并跟踪审批结果">
    <div class="metric-grid">
      <div class="metric"><span>我的待审申请</span><b>{{ myPendingCount }}</b></div>
      <div class="metric"><span>我的已通过申请</span><b>{{ myApprovedCount }}</b></div>
      <div class="metric"><span>可申请类型</span><b>{{ types.length }}</b></div>
      <div class="metric"><span>管理员待审批</span><b>{{ adminPendingCount }}</b></div>
    </div>

    <section class="panel">
      <el-tabs>
        <el-tab-pane v-if="!auth.isAdmin" label="提交申请">
          <el-form label-position="top">
            <div class="form-grid">
              <el-form-item label="申请类型">
                <el-select v-model="form.typeId" style="width: 100%" @change="onTypeChange">
                  <el-option v-for="type in types" :key="type.type_id" :label="type.type_name" :value="type.type_id" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="selectedType?.applicant_scope === 'ORG_LEADER'" label="申请组织">
                <el-select v-model="form.orgId" style="width: 100%" placeholder="选择负责组织">
                  <el-option v-for="org in managedOrgs" :key="org.org_id" :label="org.org_name" :value="org.org_id" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="resourceOptions.length" label="指定资源">
                <el-select v-model="form.resourceId" clearable style="width: 100%">
                  <el-option v-for="resource in resourceOptions" :key="resource.resource_id" :label="resourceLabel(resource)" :value="resource.resource_id" />
                </el-select>
              </el-form-item>
              <el-form-item label="申请数量">
                <el-input-number v-model="form.quantity" :min="1" style="width: 100%" />
              </el-form-item>
              <el-form-item label="期望开始时间">
                <el-date-picker v-model="form.expectedStart" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
              <el-form-item label="期望结束时间">
                <el-date-picker v-model="form.expectedEnd" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
              <el-form-item label="联系方式">
                <el-input v-model="form.contact" />
              </el-form-item>
              <el-form-item label="申请标题">
                <el-input v-model="form.title" />
              </el-form-item>
              <el-form-item class="full" label="申请理由">
                <el-input v-model="form.applyReason" type="textarea" :rows="4" />
              </el-form-item>
            </div>
            <div class="table-actions">
              <el-button type="primary" :icon="Promotion" @click="submit">提交申请</el-button>
              <el-button @click="resetForm">重置</el-button>
            </div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane v-if="!auth.isAdmin" label="我的申请">
          <div class="table-actions">
            <el-button :icon="Refresh" @click="loadMine">刷新</el-button>
          </div>
          <el-table :data="myApplications" height="430">
            <el-table-column prop="title" label="申请标题" min-width="170" />
            <el-table-column prop="type_name" label="类型" width="120" />
            <el-table-column prop="resource_name" label="资源" min-width="150" />
            <el-table-column prop="expected_start" label="开始时间" width="170" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="review_remark" label="审批说明" min-width="180" />
            <el-table-column prop="reject_reason" label="驳回原因" min-width="150" />
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.status !== 'PENDING'" @click="cancel(row)">撤销</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="auth.isAdmin" label="事务审批">
          <div class="table-actions">
            <el-select v-model="adminStatus" placeholder="状态" clearable style="width: 160px" @change="loadAdminApplications">
              <el-option label="PENDING" value="PENDING" />
              <el-option label="APPROVED" value="APPROVED" />
              <el-option label="REJECTED" value="REJECTED" />
              <el-option label="CANCELLED" value="CANCELLED" />
            </el-select>
            <el-button :icon="Refresh" @click="loadAdminApplications">刷新</el-button>
          </div>
          <el-table :data="adminApplications" height="500">
            <el-table-column prop="applicant_name" label="申请人" width="110" />
            <el-table-column prop="title" label="申请标题" min-width="170" />
            <el-table-column prop="type_name" label="类型" width="120" />
            <el-table-column prop="org_name" label="组织" min-width="140" />
            <el-table-column prop="resource_name" label="资源" min-width="150" />
            <el-table-column prop="expected_start" label="开始时间" width="170" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.status !== 'PENDING'" @click="approve(row)">通过</el-button>
                <el-button size="small" :disabled="row.status !== 'PENDING'" @click="reject(row)">驳回</el-button>
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
import { Promotion, Refresh } from '@element-plus/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const types = ref([])
const orgs = ref([])
const resourceOptions = ref([])
const myApplications = ref([])
const adminApplications = ref([])
const adminStatus = ref('PENDING')
const form = reactive(defaultForm())

const selectedType = computed(() => types.value.find((type) => type.type_id === form.typeId))
const managedOrgs = computed(() => orgs.value.filter((org) => org.principal_user_id === auth.user?.userId && org.org_status === 'ACTIVE'))
const myPendingCount = computed(() => myApplications.value.filter((row) => row.status === 'PENDING').length)
const myApprovedCount = computed(() => myApplications.value.filter((row) => row.status === 'APPROVED').length)
const adminPendingCount = computed(() => adminApplications.value.filter((row) => row.status === 'PENDING').length)

onMounted(loadAll)

function defaultForm() {
  return { typeId: null, resourceId: null, orgId: null, title: '', applyReason: '', expectedStart: '', expectedEnd: '', quantity: 1, contact: '' }
}

async function loadAll() {
  const baseTasks = [http.get('/affairs/types'), http.get('/organizations')]
  const [typeRows, orgRows] = await Promise.all(baseTasks)
  types.value = typeRows
  orgs.value = orgRows
  if (!auth.isAdmin) {
    resetForm()
    await loadMine()
  } else {
    await loadAdminApplications()
  }
}

async function loadMine() {
  myApplications.value = await http.get('/affairs/applications/mine')
}

async function loadAdminApplications() {
  adminApplications.value = await http.get('/admin/affairs/applications', { params: { status: adminStatus.value || undefined } })
}

async function onTypeChange() {
  form.resourceId = null
  form.orgId = null
  resourceOptions.value = form.typeId ? await http.get('/affairs/resources', { params: { typeId: form.typeId } }) : []
  if (selectedType.value?.applicant_scope === 'ORG_LEADER' && managedOrgs.value.length) {
    form.orgId = managedOrgs.value[0].org_id
  }
}

function resetForm() {
  Object.assign(form, defaultForm(), { typeId: types.value[0]?.type_id || null })
  if (form.typeId) onTypeChange()
}

function resourceLabel(resource) {
  return `${resource.resource_name}${resource.resource_location ? `（${resource.resource_location}）` : ''}`
}

async function submit() {
  await http.post('/affairs/applications', form)
  ElMessage.success('事务申请已提交')
  resetForm()
  await loadMine()
}

async function cancel(row) {
  await http.patch(`/affairs/applications/${row.affair_id}/cancel`)
  ElMessage.success('申请已撤销')
  await loadMine()
}

async function approve(row) {
  const { value } = await ElMessageBox.prompt('请输入审批说明', '通过事务申请', { inputValue: '审批通过，请按要求使用资源。' })
  await http.patch(`/admin/affairs/applications/${row.affair_id}/audit`, { status: 'APPROVED', reviewRemark: value })
  ElMessage.success('事务申请已通过')
  await loadAdminApplications()
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回事务申请')
  await http.patch(`/admin/affairs/applications/${row.affair_id}/audit`, { status: 'REJECTED', rejectReason: value })
  ElMessage.success('事务申请已驳回')
  await loadAdminApplications()
}
</script>
