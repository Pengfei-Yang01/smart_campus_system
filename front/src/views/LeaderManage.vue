<template>
  <!-- 负责人工作台，覆盖组织申请、组织资料维护、
       成员审核、活动发布、签到和录分。 -->
  <AppLayout title="组织负责人管理" subtitle="处理组织申请、成员审批、活动发布、状态维护、签到和录分">
    <section class="panel">
      <div class="table-actions">
        <el-select v-model="currentOrgId" placeholder="选择负责组织" style="width: 260px" @change="loadOrgWork">
          <el-option v-for="org in managedOrgs" :key="org.org_id" :label="org.org_name" :value="org.org_id" />
        </el-select>
        <el-button :icon="Plus" type="primary" @click="applyDialog = true">申请成立组织</el-button>
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </div>
      <el-tabs>
        <el-tab-pane label="组织申请进度">
          <el-table :data="myApplies" height="260">
            <el-table-column prop="org_name" label="组织名称" min-width="180" />
            <el-table-column prop="apply_reason" label="成立理由" min-width="220" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="reject_reason" label="驳回原因" min-width="150" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="组织信息维护">
          <el-form v-if="orgForm.orgId" label-position="top">
            <div class="form-grid">
              <el-form-item label="组织名称"><el-input v-model="orgForm.orgName" /></el-form-item>
              <el-form-item label="组织类型">
                <el-select v-model="orgForm.orgType" style="width: 100%">
                  <el-option label="社团" value="CLUB" />
                  <el-option label="学院组织" value="COLLEGE" />
                  <el-option label="校级组织" value="UNIVERSITY" />
                  <el-option label="其他" value="OTHER" />
                </el-select>
              </el-form-item>
              <el-form-item label="联系方式"><el-input v-model="orgForm.contact" /></el-form-item>
              <el-form-item class="full" label="简介/公告"><el-input v-model="orgForm.description" type="textarea" :rows="4" /></el-form-item>
            </div>
            <el-button type="primary" @click="saveOrg">保存组织信息</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="成员审批">
          <el-table :data="members" height="360">
            <el-table-column prop="student_no" label="学号" width="120" />
            <el-table-column prop="real_name" label="姓名" width="110" />
            <el-table-column prop="apply_reason" label="申请理由" min-width="220" />
            <el-table-column prop="join_status" label="状态" width="110" />
            <el-table-column prop="reject_reason" label="驳回原因" min-width="130" />
            <el-table-column label="操作" width="190">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.join_status !== 'PENDING'" @click="auditMember(row, 'APPROVED')">通过</el-button>
                <el-button size="small" :disabled="row.join_status !== 'PENDING'" @click="rejectMember(row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="活动管理">
          <div class="table-actions">
            <el-button type="primary" :icon="Plus" @click="openActivity()">发布活动</el-button>
          </div>
          <el-table :data="orgActivities" height="360">
            <el-table-column prop="activity_name" label="活动名称" min-width="180" />
            <el-table-column prop="start_time" label="时间" width="170" />
            <el-table-column prop="capacity" label="名额" width="80" />
            <el-table-column prop="registered_count" label="报名" width="80" />
            <el-table-column prop="activity_status" label="状态" width="110" />
            <el-table-column label="状态维护" width="360">
              <template #default="{ row }">
                <el-button size="small" @click="editActivity(row)">编辑</el-button>
                <el-button size="small" @click="$router.push(`/activities/${row.activity_id}`)">详情</el-button>
                <el-button size="small" @click="changeStatus(row, 'OPEN')">发布</el-button>
                <el-button size="small" @click="changeStatus(row, 'CLOSED')">截止</el-button>
                <el-button size="small" @click="changeStatus(row, 'FINISHED')">结束</el-button>
                <el-button size="small" @click="changeStatus(row, 'OFFLINE')">下架</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="报名名单与录分">
          <div class="table-actions">
            <el-select v-model="selectedActivityId" placeholder="选择活动" style="width: 320px" @change="loadRegistrations">
              <el-option v-for="a in orgActivities" :key="a.activity_id" :label="`${a.activity_name}（${a.activity_status}）`" :value="a.activity_id" />
            </el-select>
          </div>
          <el-table :data="registrations" height="360">
            <el-table-column prop="student_no" label="学号" width="120" />
            <el-table-column prop="real_name" label="姓名" width="110" />
            <el-table-column prop="registration_status" label="报名" width="100" />
            <el-table-column prop="checkin_status" label="签到" width="120" />
            <el-table-column label="操作" width="260">
              <template #default="{ row }">
                <el-button size="small" @click="checkin(row, 'CHECKED')">签到</el-button>
                <el-button size="small" @click="checkin(row, 'ABSENT')">缺勤</el-button>
                <el-button size="small" type="primary" @click="recordScore(row)">录分</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="applyDialog" title="申请成立组织" width="620px">
      <el-form label-position="top">
        <el-form-item label="组织名称"><el-input v-model="applyForm.orgName" /></el-form-item>
        <el-form-item label="组织类型">
          <el-select v-model="applyForm.orgType" style="width: 100%">
            <el-option label="社团" value="CLUB" /><el-option label="学院组织" value="COLLEGE" />
            <el-option label="校级组织" value="UNIVERSITY" /><el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="组织简介"><el-input v-model="applyForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="成立理由"><el-input v-model="applyForm.applyReason" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="联系方式"><el-input v-model="applyForm.contact" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="applyDialog = false">取消</el-button><el-button type="primary" @click="submitOrgApply">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="activityDialog" title="活动发布/编辑" width="760px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="活动名称"><el-input v-model="activityForm.activityName" /></el-form-item>
          <el-form-item label="活动类型">
            <el-select v-model="activityForm.typeId" style="width: 100%">
              <el-option v-for="t in types" :key="t.type_id" :label="t.type_name" :value="t.type_id" />
            </el-select>
          </el-form-item>
          <el-form-item label="开始时间"><el-date-picker v-model="activityForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
          <el-form-item label="结束时间"><el-date-picker v-model="activityForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
          <el-form-item label="报名截止"><el-date-picker v-model="activityForm.registrationDeadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
          <el-form-item label="地点"><el-input v-model="activityForm.location" /></el-form-item>
          <el-form-item label="名额"><el-input-number v-model="activityForm.capacity" :min="1" style="width: 100%" /></el-form-item>
          <el-form-item label="基础加分分值"><el-input-number v-model="activityForm.baseScore" :min="0" :step="0.5" style="width: 100%" /></el-form-item>
          <el-form-item class="full" label="活动简介"><el-input v-model="activityForm.description" type="textarea" :rows="3" /></el-form-item>
          <el-form-item class="full" label="活动要求"><el-input v-model="activityForm.requirement" type="textarea" :rows="2" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="activityDialog = false">取消</el-button><el-button type="primary" @click="saveActivity">保存</el-button></template>
    </el-dialog>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const orgs = ref([])
const myApplies = ref([])
const members = ref([])
const activities = ref([])
const registrations = ref([])
const types = ref([])
const currentOrgId = ref(null)
const selectedActivityId = ref(null)
const applyDialog = ref(false)
const activityDialog = ref(false)
const editingId = ref(null)
// 管理员可查看所有组织；负责人只查看
// 自己负责的组织。
const managedOrgs = computed(() => auth.isAdmin ? orgs.value : orgs.value.filter((o) => o.principal_user_id === auth.user?.userId))

// 活动表格按当前选中组织限定范围。
const orgActivities = computed(() => activities.value.filter((a) => !currentOrgId.value || a.org_id === currentOrgId.value))
const orgForm = reactive({ orgId: null, orgName: '', orgType: 'CLUB', description: '', contact: '' })
const applyForm = reactive({ orgName: '', orgType: 'CLUB', description: '', applyReason: '', contact: '' })
const activityForm = reactive(defaultActivity())

onMounted(loadAll)

// 创建和重置流程共用的活动表单初始值。
function defaultActivity() {
  return { activityName: '', orgId: null, typeId: null, startTime: '', endTime: '', registrationDeadline: '', location: '', capacity: 50, baseScore: 1, description: '', requirement: '', activityStatus: 'DRAFT' }
}

// 加载负责人工作台需要的全部下拉框和列表数据。
async function loadAll() {
  const [orgRows, applyRows, typeRows, activityRows] = await Promise.all([
    http.get('/organizations'), http.get('/organizations/applies/mine'), http.get('/activity-types'), http.get('/activities')
  ])
  orgs.value = orgRows
  myApplies.value = applyRows
  types.value = typeRows
  activities.value = activityRows
  if (!currentOrgId.value && managedOrgs.value.length) currentOrgId.value = managedOrgs.value[0].org_id
  await loadOrgWork()
}

// 加载依赖当前选中组织的数据。
async function loadOrgWork() {
  const org = managedOrgs.value.find((o) => o.org_id === currentOrgId.value)
  if (org) Object.assign(orgForm, { orgId: org.org_id, orgName: org.org_name, orgType: org.org_type, description: org.description, contact: org.contact })
  if (currentOrgId.value) members.value = await http.get(`/organizations/${currentOrgId.value}/members`)
}

// 保存当前选中组织的资料修改。
async function saveOrg() {
  await http.put(`/organizations/${orgForm.orgId}`, orgForm)
  ElMessage.success('组织信息已保存')
  await loadAll()
}

// 提交新的组织创建申请。
async function submitOrgApply() {
  await http.post('/organizations/apply', applyForm)
  ElMessage.success('组织成立申请已提交')
  applyDialog.value = false
  await loadAll()
}

// 通过或驳回待审核的组织成员申请。
async function auditMember(row, status, reason = '') {
  await http.patch(`/organizations/${currentOrgId.value}/members/${row.user_id}`, { joinStatus: status, rejectReason: reason })
  ElMessage.success('成员申请已处理')
  await loadOrgWork()
}

// 先询问驳回原因，再复用成员审核函数。
async function rejectMember(row) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回成员申请')
  await auditMember(row, 'REJECTED', value)
}

// 以创建模式打开活动弹窗。
function openActivity() {
  editingId.value = null
  Object.assign(activityForm, defaultActivity(), { orgId: currentOrgId.value, typeId: types.value[0]?.type_id })
  activityDialog.value = true
}

// 以编辑模式打开活动弹窗，并把后端字段名映射到表单字段。
function editActivity(row) {
  editingId.value = row.activity_id
  Object.assign(activityForm, {
    activityName: row.activity_name, orgId: row.org_id, typeId: row.type_id, startTime: row.start_time,
    endTime: row.end_time, registrationDeadline: row.registration_deadline, location: row.location,
    capacity: row.capacity, baseScore: row.base_score, description: row.description, requirement: row.requirement
  })
  activityDialog.value = true
}

// 根据当前是否正在编辑来创建或更新活动。
async function saveActivity() {
  if (editingId.value) await http.put(`/activities/${editingId.value}`, activityForm)
  else await http.post('/activities', activityForm)
  ElMessage.success('活动已保存')
  activityDialog.value = false
  await loadAll()
}

// 通过后端状态机接口切换活动生命周期状态。
async function changeStatus(row, status) {
  await http.patch(`/activities/${row.activity_id}/status`, { status })
  ElMessage.success('活动状态已更新')
  await loadAll()
}

// 加载签到标签页当前选中活动的报名记录。
async function loadRegistrations() {
  if (!selectedActivityId.value) return
  registrations.value = await http.get(`/activities/${selectedActivityId.value}/registrations`)
}

// 把一条报名记录标记为已签到或缺勤。
async function checkin(row, checkinStatus) {
  await http.patch(`/activities/${selectedActivityId.value}/registrations/${row.registration_id}/checkin`, { checkinStatus })
  await loadRegistrations()
}

// 为某个已报名用户提交积分记录，之后等待管理员审核。
async function recordScore(row) {
  await http.post('/scores', { activityId: selectedActivityId.value, userId: row.user_id })
  ElMessage.success('积分已写入并等待管理员审核')
}
</script>
