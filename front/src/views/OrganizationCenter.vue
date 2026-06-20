<template>
  <!-- 组织列表页，用户可以浏览全部组织，并打开
       详情页查看活动或申请加入。 -->
  <AppLayout title="组织中心" subtitle="查看校园组织、组织关系和组织发布的活动">
    <section class="panel">
      <div class="table-actions">
        <el-input v-model="keyword" placeholder="搜索组织名称或负责人" clearable style="width: 260px" />
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
      <el-table :data="filtered" height="520" @row-click="row => $router.push(`/organizations/${row.org_id}`)">
        <el-table-column prop="org_name" label="组织名称" min-width="180" />
        <el-table-column prop="org_type" label="类型" width="120" />
        <el-table-column prop="principal_name" label="负责人" width="120" />
        <el-table-column prop="org_status" label="状态" width="120" />
        <el-table-column prop="created_at" label="创建时间" width="170" />
        <el-table-column label="我的关系" width="130">
          <template #default="{ row }"><el-tag>{{ row.my_status || '未加入' }}</el-tag></template>
        </el-table-column>
      </el-table>
    </section>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

const rows = ref([])
const keyword = ref('')

// 前端按组织名称、负责人和类型进行关键字筛选。
const filtered = computed(() => rows.value.filter((row) => {
  const text = `${row.org_name} ${row.principal_name} ${row.org_type}`
  return !keyword.value || text.includes(keyword.value)
}))

onMounted(load)

// 加载组织列表及当前用户的成员关系状态。
async function load() {
  rows.value = await http.get('/organizations')
}
</script>
