<template>
  <!-- 活动列表页。用户可以筛选活动，并打开详情页进行
       报名或取消报名。 -->
  <AppLayout title="活动中心" subtitle="按类型、状态和关键词筛选活动，查看剩余名额并进入详情报名">
    <section class="panel">
      <div class="table-actions">
        <el-input v-model="query.keyword" placeholder="活动名称、简介、组织" clearable style="width: 240px" />
        <el-select v-model="query.typeId" placeholder="活动类型" clearable style="width: 160px">
          <el-option v-for="t in types" :key="t.type_id" :label="t.type_name" :value="t.type_id" />
        </el-select>
        <el-select v-model="query.status" placeholder="活动状态" clearable style="width: 150px">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button :icon="Search" @click="load">查询</el-button>
      </div>
      <el-table :data="pageRows" height="520" @row-click="row => $router.push(`/activities/${row.activity_id}`)">
        <el-table-column prop="activity_name" label="活动名称" min-width="190" />
        <el-table-column prop="type_name" label="类型" width="110" />
        <el-table-column prop="org_name" label="主办组织" width="150" />
        <el-table-column prop="start_time" label="活动时间" width="170" />
        <el-table-column prop="location" label="地点" width="140" />
        <el-table-column label="剩余名额" width="100">
          <template #default="{ row }">{{ row.capacity - row.registered_count }}</template>
        </el-table-column>
        <el-table-column prop="base_score" label="基础分" width="90" />
        <el-table-column prop="activity_status" label="状态" width="110" />
        <el-table-column label="报名状态" width="110">
          <template #default="{ row }"><el-tag :type="row.registered ? 'success' : 'info'">{{ row.registered ? '已报名' : '未报名' }}</el-tag></template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :page-size="pageSize" layout="total, prev, pager, next" :total="rows.length" style="margin-top: 14px" />
    </section>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

const types = ref([])
const rows = ref([])
const page = ref(1)
const pageSize = 10
const statuses = ['DRAFT', 'OPEN', 'CLOSED', 'FINISHED', 'OFFLINE']
// 查询对象会作为地址参数传给活动列表接口。
const query = reactive({ keyword: '', typeId: null, status: '' })

// 本项目使用前端分页，让后端接口保持简单。
const pageRows = computed(() => rows.value.slice((page.value - 1) * pageSize, page.value * pageSize))

// 先加载字典数据，让类型筛选器可以立即渲染。
onMounted(async () => {
  types.value = await http.get('/activity-types')
  await load()
})

// 按当前筛选条件获取活动，并重置到第一页。
async function load() {
  rows.value = await http.get('/activities', { params: query })
  page.value = 1
}
</script>
