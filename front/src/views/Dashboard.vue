<template>
  <!-- 保留的旧版综合仪表盘，仅作参考。当前路由使用
       StudentHome、LeaderManage、AdminDashboard 等拆分后的页面。 -->
  <div class="page">
    <div class="shell">
      <header class="topbar">
        <div class="brand">
          <span class="brand-mark"><School /></span>
          <span>智慧校园综测服务平台</span>
        </div>
        <div class="toolbar">
          <el-tag>{{ auth.user?.realName }}</el-tag>
          <el-tag v-for="role in auth.roles" :key="role" type="info">{{ role }}</el-tag>
          <el-button :icon="SwitchButton" @click="logout">退出</el-button>
        </div>
      </header>

      <el-tabs v-model="active" class="panel" @tab-change="refreshTab">
        <el-tab-pane label="学生首页" name="home">
          <div class="metric-grid">
            <div class="metric"><span>有效报名</span><b>{{ dashboard.registrationCount || 0 }}</b></div>
            <div class="metric"><span>待审积分</span><b>{{ dashboard.pendingScoreCount || 0 }}</b></div>
            <div class="metric"><span>已通过积分</span><b>{{ dashboard.approvedScore?.total || 0 }}</b></div>
            <div class="metric"><span>负责人申请</span><b>{{ dashboard.leaderApply?.status || '无' }}</b></div>
          </div>
          <div class="grid-2">
            <section>
              <h2 class="section-title">近期活动</h2>
              <el-table :data="dashboard.recentActivities || []" height="360">
                <el-table-column prop="activity_name" label="活动名称" min-width="180" />
                <el-table-column prop="type_name" label="类型" width="110" />
                <el-table-column prop="org_name" label="组织" width="150" />
                <el-table-column prop="start_time" label="开始时间" width="170" />
                <el-table-column prop="activity_status" label="状态" width="100" />
              </el-table>
            </section>
            <section>
              <h2 class="section-title">申请组织负责人</h2>
              <el-form label-position="top" @submit.prevent>
                <el-form-item label="申请理由">
                  <el-input v-model="leaderForm.applyReason" type="textarea" :rows="4" />
                </el-form-item>
                <el-form-item label="联系方式">
                  <el-input v-model="leaderForm.contact" />
                </el-form-item>
                <el-form-item label="相关经历">
                  <el-input v-model="leaderForm.experience" type="textarea" :rows="3" />
                </el-form-item>
                <el-button type="primary" :icon="Promotion" @click="submitLeaderApply">提交申请</el-button>
              </el-form>
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane label="活动中心" name="activities">
          <div class="table-actions">
            <el-input v-model="activityQuery.keyword" placeholder="关键词" clearable style="width: 220px" />
            <el-select v-model="activityQuery.typeId" placeholder="活动类型" clearable style="width: 160px">
              <el-option v-for="t in types" :key="t.type_id" :label="t.type_name" :value="t.type_id" />
            </el-select>
            <el-select v-model="activityQuery.status" placeholder="状态" clearable style="width: 150px">
              <el-option v-for="s in activityStatuses" :key="s" :label="s" :value="s" />
            </el-select>
            <el-button :icon="Search" @click="loadActivities">查询</el-button>
            <el-button v-if="auth.isLeader || auth.isAdmin" type="primary" :icon="Plus" @click="openActivity()">发布活动</el-button>
          </div>
          <el-table :data="activities" height="520" @row-click="showActivity">
            <el-table-column prop="activity_name" label="活动名称" min-width="180" />
            <el-table-column prop="type_name" label="类型" width="110" />
            <el-table-column prop="org_name" label="组织" width="150" />
            <el-table-column prop="start_time" label="开始时间" width="170" />
            <el-table-column prop="capacity" label="名额" width="80" />
            <el-table-column prop="registered_count" label="已报名" width="90" />
            <el-table-column prop="activity_status" label="状态" width="110" />
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.registered || row.activity_status !== 'OPEN'" @click.stop="registerActivity(row)">报名</el-button>
                <el-button size="small" :disabled="!row.registered" @click.stop="cancelActivity(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="组织中心" name="orgs">
          <div class="table-actions">
            <el-button :icon="Refresh" @click="loadOrgs">刷新</el-button>
            <el-button v-if="auth.isLeader" type="primary" :icon="Plus" @click="orgApplyVisible = true">申请成立组织</el-button>
          </div>
          <el-table :data="orgs" height="520" @row-click="showOrg">
            <el-table-column prop="org_name" label="组织名称" min-width="180" />
            <el-table-column prop="org_type" label="类型" width="120" />
            <el-table-column prop="principal_name" label="负责人" width="120" />
            <el-table-column prop="org_status" label="状态" width="120" />
            <el-table-column prop="my_status" label="我的关系" width="130" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.my_status === 'APPROVED' || row.my_status === 'PENDING'" @click.stop="joinOrg(row)">申请加入</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="我的活动与积分" name="mine">
          <div class="grid-2">
            <section>
              <h2 class="section-title">报名记录</h2>
              <el-table :data="myRegistrations" height="420">
                <el-table-column prop="activity_name" label="活动" min-width="180" />
                <el-table-column prop="registration_status" label="报名状态" width="110" />
                <el-table-column prop="checkin_status" label="签到" width="110" />
                <el-table-column prop="registered_at" label="报名时间" width="170" />
              </el-table>
            </section>
            <section>
              <h2 class="section-title">积分明细</h2>
              <el-table :data="myScores" height="420">
                <el-table-column prop="activity_name" label="活动" min-width="160" />
                <el-table-column prop="identity_type" label="身份" width="120" />
                <el-table-column prop="final_score" label="分值" width="90" />
                <el-table-column prop="audit_status" label="审核" width="110" />
              </el-table>
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane label="AI 助手" name="ai">
          <el-empty description="AI 问答模块已按要求暂不实现，数据库保留问答记录表。" />
        </el-tab-pane>

        <el-tab-pane v-if="auth.isLeader || auth.isAdmin" label="负责人工作台" name="leader">
          <div class="table-actions">
            <el-select v-model="leaderOrgId" placeholder="选择组织" style="width: 220px" @change="loadLeaderData">
              <el-option v-for="o in managedOrgs" :key="o.org_id" :label="o.org_name" :value="o.org_id" />
            </el-select>
            <el-button :icon="Refresh" @click="loadLeaderData">刷新</el-button>
          </div>
          <el-tabs>
            <el-tab-pane label="成员审批">
              <el-table :data="orgMembers" height="420">
                <el-table-column prop="student_no" label="学号" width="120" />
                <el-table-column prop="real_name" label="姓名" width="110" />
                <el-table-column prop="apply_reason" label="申请理由" min-width="180" />
                <el-table-column prop="join_status" label="状态" width="120" />
                <el-table-column label="操作" width="180">
                  <template #default="{ row }">
                    <el-button size="small" :disabled="row.join_status !== 'PENDING'" @click="auditMember(row, 'APPROVED')">通过</el-button>
                    <el-button size="small" :disabled="row.join_status !== 'PENDING'" @click="auditMember(row, 'REJECTED')">驳回</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="活动录分">
              <div class="table-actions">
                <el-select v-model="scoreForm.activityId" placeholder="已结束活动" style="width: 260px" @change="loadRegistrationsForScore">
                  <el-option v-for="a in finishedActivities" :key="a.activity_id" :label="a.activity_name" :value="a.activity_id" />
                </el-select>
              </div>
              <el-table :data="scoreRegistrations" height="420">
                <el-table-column prop="student_no" label="学号" width="120" />
                <el-table-column prop="real_name" label="姓名" width="110" />
                <el-table-column prop="checkin_status" label="签到" width="120" />
                <el-table-column label="操作" width="150">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" @click="recordScore(row)">录分</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>

        <el-tab-pane v-if="auth.isAdmin" label="后台管理" name="admin">
          <div class="metric-grid">
            <div class="metric"><span>学生</span><b>{{ adminStats.studentCount || 0 }}</b></div>
            <div class="metric"><span>活动</span><b>{{ adminStats.activityCount || 0 }}</b></div>
            <div class="metric"><span>组织</span><b>{{ adminStats.organizationCount || 0 }}</b></div>
            <div class="metric"><span>待审积分</span><b>{{ adminStats.pendingScoreCount || 0 }}</b></div>
          </div>
          <el-tabs>
            <el-tab-pane label="负责人申请">
              <el-table :data="leaderApplies" height="360">
                <el-table-column prop="real_name" label="姓名" width="110" />
                <el-table-column prop="apply_reason" label="理由" min-width="220" />
                <el-table-column prop="status" label="状态" width="110" />
                <el-table-column label="操作" width="180">
                  <template #default="{ row }">
                    <el-button size="small" :disabled="row.status !== 'PENDING'" @click="auditLeaderApply(row, 'APPROVED')">通过</el-button>
                    <el-button size="small" :disabled="row.status !== 'PENDING'" @click="auditLeaderApply(row, 'REJECTED')">驳回</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="组织申请">
              <el-table :data="orgApplies" height="360">
                <el-table-column prop="org_name" label="组织" width="180" />
                <el-table-column prop="applicant_name" label="申请人" width="110" />
                <el-table-column prop="apply_reason" label="理由" min-width="220" />
                <el-table-column prop="status" label="状态" width="110" />
                <el-table-column label="操作" width="180">
                  <template #default="{ row }">
                    <el-button size="small" :disabled="row.status !== 'PENDING'" @click="auditOrgApply(row, 'APPROVED')">通过</el-button>
                    <el-button size="small" :disabled="row.status !== 'PENDING'" @click="auditOrgApply(row, 'REJECTED')">驳回</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="积分审核">
              <el-table :data="scoreRecords" height="360">
                <el-table-column prop="real_name" label="学生" width="110" />
                <el-table-column prop="activity_name" label="活动" min-width="180" />
                <el-table-column prop="final_score" label="分值" width="90" />
                <el-table-column prop="audit_status" label="状态" width="110" />
                <el-table-column label="操作" width="180">
                  <template #default="{ row }">
                    <el-button size="small" :disabled="row.audit_status !== 'PENDING'" @click="auditScore(row, 'APPROVED')">通过</el-button>
                    <el-button size="small" :disabled="row.audit_status !== 'PENDING'" @click="auditScore(row, 'REJECTED')">驳回</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="学生管理">
              <el-table :data="students" height="360">
                <el-table-column prop="student_no" label="学号" width="120" />
                <el-table-column prop="real_name" label="姓名" width="110" />
                <el-table-column prop="roles" label="角色" min-width="160" />
                <el-table-column prop="account_status" label="账号" width="110" />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="activityDialog" title="活动信息" width="720px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="活动名称"><el-input v-model="activityForm.activityName" /></el-form-item>
          <el-form-item label="主办组织">
            <el-select v-model="activityForm.orgId" style="width: 100%">
              <el-option v-for="o in managedOrgs" :key="o.org_id" :label="o.org_name" :value="o.org_id" />
            </el-select>
          </el-form-item>
          <el-form-item label="活动类型">
            <el-select v-model="activityForm.typeId" style="width: 100%">
              <el-option v-for="t in types" :key="t.type_id" :label="t.type_name" :value="t.type_id" />
            </el-select>
          </el-form-item>
          <el-form-item label="名额"><el-input-number v-model="activityForm.capacity" :min="1" style="width: 100%" /></el-form-item>
          <el-form-item label="开始时间"><el-date-picker v-model="activityForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
          <el-form-item label="结束时间"><el-date-picker v-model="activityForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
          <el-form-item label="报名截止"><el-date-picker v-model="activityForm.registrationDeadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
          <el-form-item label="地点"><el-input v-model="activityForm.location" /></el-form-item>
          <el-form-item class="full" label="简介"><el-input v-model="activityForm.description" type="textarea" :rows="3" /></el-form-item>
          <el-form-item class="full" label="要求"><el-input v-model="activityForm.requirement" type="textarea" :rows="2" /></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="activityDialog = false">取消</el-button>
        <el-button type="primary" @click="saveActivity">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="orgApplyVisible" title="申请成立组织" width="620px">
      <el-form label-position="top">
        <el-form-item label="组织名称"><el-input v-model="orgApplyForm.orgName" /></el-form-item>
        <el-form-item label="组织类型">
          <el-select v-model="orgApplyForm.orgType" style="width: 100%">
            <el-option label="社团" value="CLUB" />
            <el-option label="学院组织" value="COLLEGE" />
            <el-option label="校级组织" value="UNIVERSITY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介"><el-input v-model="orgApplyForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="成立理由"><el-input v-model="orgApplyForm.applyReason" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orgApplyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOrgApply">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Promotion, Refresh, School, Search, SwitchButton } from '@element-plus/icons-vue'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const active = ref('home')
const types = ref([])
const dashboard = ref({})
const activities = ref([])
const orgs = ref([])
const myRegistrations = ref([])
const myScores = ref([])
const adminStats = ref({})
const leaderApplies = ref([])
const orgApplies = ref([])
const scoreRecords = ref([])
const students = ref([])
const orgMembers = ref([])
const scoreRegistrations = ref([])
const leaderOrgId = ref(null)
const activityDialog = ref(false)
const orgApplyVisible = ref(false)

const activityStatuses = ['DRAFT', 'OPEN', 'CLOSED', 'FINISHED', 'OFFLINE']
const activityQuery = reactive({ keyword: '', typeId: null, status: '' })
const leaderForm = reactive({ applyReason: '', contact: '', experience: '' })
const activityForm = reactive(defaultActivity())
const orgApplyForm = reactive({ orgName: '', orgType: 'CLUB', description: '', applyReason: '', contact: '' })
const scoreForm = reactive({ activityId: null })

// 旧综合页中当前用户可见的组织列表。
const managedOrgs = computed(() => auth.isAdmin ? orgs.value : orgs.value.filter((o) => o.principal_user_id === auth.user?.userId))

// 已结束活动可以作为录入积分的候选活动。
const finishedActivities = computed(() => activities.value.filter((a) => a.activity_status === 'FINISHED' && (!leaderOrgId.value || a.org_id === leaderOrgId.value)))

onMounted(async () => {
  await Promise.all([loadTypes(), loadDashboard(), loadActivities(), loadOrgs()])
})

// 活动弹窗的初始值。
function defaultActivity() {
  return {
    activityName: '',
    orgId: null,
    typeId: null,
    capacity: 50,
    startTime: '',
    endTime: '',
    registrationDeadline: '',
    location: '',
    description: '',
    requirement: '',
    activityStatus: 'OPEN'
  }
}

// 清空会话并返回登录页。
function logout() {
  auth.logout()
  router.push('/login')
}

// 旧综合页切换标签时按需刷新数据。
async function refreshTab(name) {
  if (name === 'home') await loadDashboard()
  if (name === 'activities') await loadActivities()
  if (name === 'orgs') await loadOrgs()
  if (name === 'mine') await loadMine()
  if (name === 'leader') await loadLeaderData()
  if (name === 'admin') await loadAdmin()
}

// 加载表单和筛选器使用的活动类型字典。
async function loadTypes() {
  types.value = await http.get('/activity-types')
}

// 加载学生首页指标。
async function loadDashboard() {
  dashboard.value = await http.get('/dashboard/student')
}

// 按当前筛选模型加载活动列表。
async function loadActivities() {
  activities.value = await http.get('/activities', { params: activityQuery })
}

// 加载组织列表，并在需要时选择默认负责人组织。
async function loadOrgs() {
  orgs.value = await http.get('/organizations')
  if (!leaderOrgId.value && managedOrgs.value.length) leaderOrgId.value = managedOrgs.value[0].org_id
}

// 加载个人报名记录。
async function loadMine() {
  myRegistrations.value = await http.get('/me/registrations')
  myScores.value = await http.get('/me/scores')
}

// 加载管理员概览和审核队列。
async function loadAdmin() {
  if (!auth.isAdmin) return
  const [stats, leaders, orgApplyRows, scores, studentRows] = await Promise.all([
    http.get('/admin/stats'),
    http.get('/admin/leader-applies'),
    http.get('/admin/org-applies'),
    http.get('/scores'),
    http.get('/admin/students')
  ])
  adminStats.value = stats
  leaderApplies.value = leaders
  orgApplies.value = orgApplyRows
  scoreRecords.value = scores
  students.value = studentRows
}

// 加载负责人相关的成员和活动数据。
async function loadLeaderData() {
  await Promise.all([loadOrgs(), loadActivities()])
  if (!leaderOrgId.value) return
  orgMembers.value = await http.get(`/organizations/${leaderOrgId.value}/members`)
}

// 加载录入积分所需的报名记录。
async function loadRegistrationsForScore() {
  if (!scoreForm.activityId) return
  scoreRegistrations.value = await http.get(`/activities/${scoreForm.activityId}/registrations`)
}

// 从旧学生标签页提交组织负责人申请。
async function submitLeaderApply() {
  await http.post('/students/leader-apply', leaderForm)
  ElMessage.success('申请已提交')
  await loadDashboard()
}

// 为当前用户报名选中的活动。
async function registerActivity(row) {
  await http.post(`/activities/${row.activity_id}/register`)
  ElMessage.success('报名成功')
  await loadActivities()
}

// 取消当前用户报名。
async function cancelActivity(row) {
  await http.delete(`/activities/${row.activity_id}/register`)
  ElMessage.success('已取消报名')
  await loadActivities()
}

// 打开创建活动弹窗，并预选默认组织和类型。
function openActivity() {
  Object.assign(activityForm, defaultActivity())
  activityForm.orgId = managedOrgs.value[0]?.org_id || null
  activityForm.typeId = types.value[0]?.type_id || null
  activityDialog.value = true
}

// 从旧弹窗保存新活动。
async function saveActivity() {
  await http.post('/activities', activityForm)
  ElMessage.success('活动已保存')
  activityDialog.value = false
  await loadActivities()
}

// 用简单弹窗显示活动简介。
async function showActivity(row) {
  const detail = await http.get(`/activities/${row.activity_id}`)
  ElMessageBox.alert(detail.description || '暂无简介', detail.activity_name, {
    confirmButtonText: '知道了'
  })
}

// 用简单弹窗显示组织简介。
async function showOrg(row) {
  const detail = await http.get(`/organizations/${row.org_id}`)
  ElMessageBox.alert(detail.description || '暂无简介', detail.org_name, {
    confirmButtonText: '知道了'
  })
}

// 使用默认理由申请加入组织。
async function joinOrg(row) {
  await http.post(`/organizations/${row.org_id}/join`, { applyReason: '希望参与组织活动' })
  ElMessage.success('加入申请已提交')
  await loadOrgs()
}

// 提交新的组织申请。
async function submitOrgApply() {
  await http.post('/organizations/apply', orgApplyForm)
  ElMessage.success('组织成立申请已提交')
  orgApplyVisible.value = false
}

// 通过或驳回成员申请。
async function auditMember(row, status) {
  await http.patch(`/organizations/${leaderOrgId.value}/members/${row.user_id}`, { joinStatus: status })
  ElMessage.success('成员申请已处理')
  await loadLeaderData()
}

// 为选中的报名记录录入积分。
async function recordScore(row) {
  await http.post('/scores', { activityId: scoreForm.activityId, userId: row.user_id })
  ElMessage.success('积分记录已提交审核')
}

// 管理员审核组织负责人申请。
async function auditLeaderApply(row, status) {
  await http.patch(`/admin/leader-applies/${row.apply_id}`, { status })
  ElMessage.success('负责人申请已处理')
  await loadAdmin()
}

// 管理员审核组织创建申请。
async function auditOrgApply(row, status) {
  await http.patch(`/admin/org-applies/${row.org_apply_id}`, { status })
  ElMessage.success('组织申请已处理')
  await loadAdmin()
  await loadOrgs()
}

// 管理员审核积分记录。
async function auditScore(row, status) {
  await http.patch(`/scores/${row.score_id}/audit`, { auditStatus: status })
  ElMessage.success('积分审核已处理')
  await loadAdmin()
}
</script>
