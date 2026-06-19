<template>
  <AppLayout title="组织详情" subtitle="查看组织简介、发布活动，并提交加入申请">
    <section class="panel">
      <div class="table-actions">
        <el-button @click="$router.back()">返回</el-button>
        <el-button type="primary" :disabled="myStatus === 'APPROVED' || myStatus === 'PENDING' || detail.org_status !== 'ACTIVE'" @click="joinVisible = true">申请加入</el-button>
        <el-button v-if="canManage" @click="$router.push('/leader')">进入组织管理</el-button>
      </div>
      <h2 class="section-title">{{ detail.org_name }}</h2>
      <div class="detail-list">
        <div><span>组织类型</span><b>{{ detail.org_type }}</b></div>
        <div><span>负责人</span><b>{{ detail.principal_name }}</b></div>
        <div><span>组织状态</span><b>{{ detail.org_status }}</b></div>
        <div><span>我与组织关系</span><b>{{ myStatus || '未加入' }}</b></div>
        <div><span>联系方式</span><b>{{ detail.contact || '-' }}</b></div>
        <div><span>创建时间</span><b>{{ detail.created_at }}</b></div>
      </div>
      <el-divider />
      <p>{{ detail.description || '暂无简介' }}</p>
    </section>

    <section class="panel" style="margin-top: 16px">
      <h2 class="section-title">该组织发布的活动</h2>
      <el-table :data="detail.activities || []" height="360" @row-click="row => $router.push(`/activities/${row.activity_id}`)">
        <el-table-column prop="activity_name" label="活动名称" min-width="180" />
        <el-table-column prop="type_name" label="类型" width="110" />
        <el-table-column prop="start_time" label="时间" width="170" />
        <el-table-column prop="activity_status" label="状态" width="110" />
      </el-table>
    </section>

    <el-dialog v-model="joinVisible" title="申请加入组织" width="520px">
      <el-form label-position="top">
        <el-form-item label="申请理由">
          <el-input v-model="joinReason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="joinVisible = false">取消</el-button>
        <el-button type="primary" @click="join">提交</el-button>
      </template>
    </el-dialog>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const detail = ref({})
const orgRows = ref([])
const joinVisible = ref(false)
const joinReason = ref('')
const myStatus = computed(() => orgRows.value.find((o) => String(o.org_id) === String(route.params.id))?.my_status)
const canManage = computed(() => auth.isAdmin || (auth.isLeader && detail.value.principal_user_id === auth.user?.userId))

onMounted(load)
async function load() {
  const [orgDetail, orgList] = await Promise.all([
    http.get(`/organizations/${route.params.id}`),
    http.get('/organizations')
  ])
  detail.value = orgDetail
  orgRows.value = orgList
}

async function join() {
  await http.post(`/organizations/${route.params.id}/join`, { applyReason: joinReason.value })
  ElMessage.success('加入申请已提交')
  joinVisible.value = false
  await load()
}
</script>
