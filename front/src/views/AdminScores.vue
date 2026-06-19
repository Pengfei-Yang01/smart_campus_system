<template>
  <AppLayout title="积分审核" subtitle="审核负责人提交的活动综测积分，并维护活动类型默认积分规则">
    <section class="panel">
      <el-tabs>
        <el-tab-pane label="积分记录审核">
          <div class="table-actions">
            <el-select v-model="status" placeholder="审核状态" clearable style="width: 160px" @change="loadScores">
              <el-option label="PENDING" value="PENDING" /><el-option label="APPROVED" value="APPROVED" /><el-option label="REJECTED" value="REJECTED" />
            </el-select>
          </div>
          <el-table :data="scores" height="410">
            <el-table-column prop="real_name" label="学生" width="110" />
            <el-table-column prop="activity_name" label="活动" min-width="180" />
            <el-table-column prop="identity_type" label="身份" width="120" />
            <el-table-column prop="base_score" label="基础分" width="90" />
            <el-table-column prop="identity_weight" label="权重" width="90" />
            <el-table-column prop="final_score" label="最终分" width="90" />
            <el-table-column prop="audit_status" label="审核" width="110" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.audit_status !== 'PENDING'" @click="auditScore(row, 'APPROVED')">通过</el-button>
                <el-button size="small" :disabled="row.audit_status !== 'PENDING'" @click="rejectScore(row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="积分规则">
          <el-table :data="rules" height="410">
            <el-table-column prop="type_name" label="活动类型" width="130" />
            <el-table-column prop="base_score" label="基础分" width="90" />
            <el-table-column prop="normal_weight" label="普通权重" width="100" />
            <el-table-column prop="member_weight" label="成员权重" width="100" />
            <el-table-column prop="leader_weight" label="负责人权重" width="110" />
            <el-table-column prop="effective_status" label="状态" width="110" />
            <el-table-column prop="rule_desc" label="说明" min-width="180" />
            <el-table-column label="操作" width="90"><template #default="{ row }"><el-button size="small" @click="openRule(row)">编辑</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="ruleVisible" title="编辑积分规则" width="620px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="基础分"><el-input-number v-model="ruleForm.baseScore" :min="0" :step="0.5" style="width: 100%" /></el-form-item>
          <el-form-item label="普通学生权重"><el-input-number v-model="ruleForm.normalWeight" :min="0.1" :step="0.1" style="width: 100%" /></el-form-item>
          <el-form-item label="组织成员权重"><el-input-number v-model="ruleForm.memberWeight" :min="0.1" :step="0.1" style="width: 100%" /></el-form-item>
          <el-form-item label="组织负责人权重"><el-input-number v-model="ruleForm.leaderWeight" :min="0.1" :step="0.1" style="width: 100%" /></el-form-item>
          <el-form-item label="状态"><el-select v-model="ruleForm.effectiveStatus" style="width: 100%"><el-option label="ENABLED" value="ENABLED" /><el-option label="DISABLED" value="DISABLED" /></el-select></el-form-item>
          <el-form-item class="full" label="说明"><el-input v-model="ruleForm.ruleDesc" type="textarea" :rows="3" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="ruleVisible = false">取消</el-button><el-button type="primary" @click="saveRule">保存</el-button></template>
    </el-dialog>
  </AppLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

const scores = ref([])
const rules = ref([])
const status = ref('PENDING')
const ruleVisible = ref(false)
const ruleId = ref(null)
const ruleForm = reactive({ baseScore: 0, normalWeight: 1, memberWeight: 1.2, leaderWeight: 1.5, effectiveStatus: 'ENABLED', ruleDesc: '' })
onMounted(loadAll)

async function loadAll() {
  await Promise.all([loadScores(), loadRules()])
}

async function loadScores() {
  scores.value = await http.get('/scores', { params: { status: status.value } })
}

async function loadRules() {
  rules.value = await http.get('/score-rules')
}

async function auditScore(row, auditStatus, rejectReason = '') {
  await http.patch(`/scores/${row.score_id}/audit`, { auditStatus, rejectReason })
  ElMessage.success('积分审核已处理')
  await loadScores()
}

async function rejectScore(row) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回积分记录')
  await auditScore(row, 'REJECTED', value)
}

function openRule(row) {
  ruleId.value = row.rule_id
  Object.assign(ruleForm, {
    baseScore: Number(row.base_score), normalWeight: Number(row.normal_weight),
    memberWeight: Number(row.member_weight), leaderWeight: Number(row.leader_weight),
    effectiveStatus: row.effective_status, ruleDesc: row.rule_desc
  })
  ruleVisible.value = true
}

async function saveRule() {
  await http.put(`/scores/rules/${ruleId.value}`, ruleForm)
  ElMessage.success('积分规则已保存')
  ruleVisible.value = false
  await loadRules()
}
</script>
