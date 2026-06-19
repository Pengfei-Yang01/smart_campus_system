<template>
  <AppLayout title="管理员学生管理" subtitle="学生查询、账号启停、信息修改、负责人申请审批和学生详情追溯">
    <section class="panel">
      <div class="table-actions">
        <el-input v-model="filters.keyword" placeholder="学号、姓名、账号" clearable style="width: 220px" />
        <el-select v-model="filters.accountStatus" placeholder="账号状态" clearable style="width: 140px">
          <el-option label="ENABLED" value="ENABLED" /><el-option label="DISABLED" value="DISABLED" />
        </el-select>
        <el-select v-model="filters.applyStatus" placeholder="申请状态" clearable style="width: 150px">
          <el-option label="PENDING" value="PENDING" /><el-option label="APPROVED" value="APPROVED" /><el-option label="REJECTED" value="REJECTED" />
        </el-select>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
      <el-table :data="filtered" height="520">
        <el-table-column prop="student_no" label="学号" width="120" />
        <el-table-column prop="username" label="账号" width="120" />
        <el-table-column prop="real_name" label="姓名" width="110" />
        <el-table-column prop="roles" label="系统角色" min-width="160" />
        <el-table-column prop="account_status" label="账号状态" width="110" />
        <el-table-column label="负责人申请" width="130">
          <template #default="{ row }">{{ applyMap[row.user_id]?.status || '未申请' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">修改/启停</el-button>
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button size="small" :disabled="applyMap[row.user_id]?.status !== 'PENDING'" @click="auditLeader(row, 'APPROVED')">通过</el-button>
            <el-button size="small" :disabled="applyMap[row.user_id]?.status !== 'PENDING'" @click="rejectLeader(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="editVisible" title="修改学生信息" width="620px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="姓名"><el-input v-model="editForm.realName" /></el-form-item>
          <el-form-item label="账号状态">
            <el-select v-model="editForm.accountStatus" style="width: 100%"><el-option label="ENABLED" value="ENABLED" /><el-option label="DISABLED" value="DISABLED" /></el-select>
          </el-form-item>
          <el-form-item label="电话"><el-input v-model="editForm.phone" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="editForm.email" /></el-form-item>
          <el-form-item label="学院"><el-input v-model="editForm.college" /></el-form-item>
          <el-form-item label="专业"><el-input v-model="editForm.major" /></el-form-item>
          <el-form-item label="班级"><el-input v-model="editForm.className" /></el-form-item>
          <el-form-item label="年级"><el-input v-model="editForm.grade" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" @click="saveStudent">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="学生详情" width="880px">
      <div v-if="detail.user_id">
        <div class="detail-list">
          <div><span>学号</span><b>{{ detail.student_no }}</b></div>
          <div><span>姓名</span><b>{{ detail.real_name }}</b></div>
          <div><span>角色</span><b>{{ detail.roles }}</b></div>
          <div><span>负责人申请</span><b>{{ detail.leaderApply?.status || '未申请' }}</b></div>
        </div>
        <el-tabs style="margin-top: 12px">
          <el-tab-pane label="报名活动"><el-table :data="detail.registrations || []" height="240"><el-table-column prop="activity_name" label="活动" /><el-table-column prop="registration_status" label="报名" width="100" /><el-table-column prop="checkin_status" label="签到" width="100" /></el-table></el-tab-pane>
          <el-tab-pane label="加入组织"><el-table :data="detail.joinedOrgs || []" height="240"><el-table-column prop="org_name" label="组织" /><el-table-column prop="member_role" label="身份" width="100" /><el-table-column prop="join_status" label="状态" width="110" /></el-table></el-tab-pane>
          <el-tab-pane label="负责组织"><el-table :data="detail.leadingOrgs || []" height="240"><el-table-column prop="org_name" label="组织" /><el-table-column prop="org_status" label="状态" width="110" /></el-table></el-tab-pane>
          <el-tab-pane label="积分记录"><el-table :data="detail.scores || []" height="240"><el-table-column prop="activity_name" label="活动" /><el-table-column prop="final_score" label="分值" width="90" /><el-table-column prop="audit_status" label="审核" width="110" /></el-table></el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

const students = ref([])
const applies = ref([])
const editVisible = ref(false)
const detailVisible = ref(false)
const editId = ref(null)
const detail = ref({})
const filters = reactive({ keyword: '', accountStatus: '', applyStatus: '' })
const editForm = reactive({ realName: '', accountStatus: 'ENABLED', phone: '', email: '', college: '', major: '', className: '', grade: '' })
const applyMap = computed(() => Object.fromEntries(applies.value.map((a) => [a.user_id, a])))
const filtered = computed(() => students.value.filter((row) => {
  const kw = `${row.student_no} ${row.username} ${row.real_name}`
  return (!filters.keyword || kw.includes(filters.keyword)) &&
    (!filters.accountStatus || row.account_status === filters.accountStatus) &&
    (!filters.applyStatus || applyMap.value[row.user_id]?.status === filters.applyStatus)
}))

onMounted(load)
async function load() {
  const [studentRows, applyRows] = await Promise.all([http.get('/admin/students'), http.get('/admin/leader-applies')])
  students.value = studentRows
  applies.value = applyRows
}

function openEdit(row) {
  editId.value = row.user_id
  Object.assign(editForm, { realName: row.real_name, accountStatus: row.account_status, phone: row.phone, email: row.email, college: row.college, major: row.major, className: row.className, grade: row.grade })
  editVisible.value = true
}

async function saveStudent() {
  await http.patch(`/admin/students/${editId.value}`, editForm)
  ElMessage.success('学生信息已保存')
  editVisible.value = false
  await load()
}

async function openDetail(row) {
  detail.value = await http.get(`/admin/students/${row.user_id}`)
  detailVisible.value = true
}

async function auditLeader(row, status, rejectReason = '') {
  await http.patch(`/admin/leader-applies/${applyMap.value[row.user_id].apply_id}`, { status, rejectReason })
  ElMessage.success('负责人申请已处理')
  await load()
}

async function rejectLeader(row) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回负责人申请')
  await auditLeader(row, 'REJECTED', value)
}
</script>
