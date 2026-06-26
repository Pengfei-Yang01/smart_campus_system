<template>
  <!-- 活动列表页。用户可以筛选活动，并打开详情页进行
       报名或取消报名。 -->
  <AppLayout title="活动中心" subtitle="按类型、状态和关键词筛选活动，查看剩余名额并进入详情报名">
    <section class="panel">
      <div class="table-actions">
        <el-input
          v-model="query.keyword"
          placeholder="活动名称、简介、组织"
          clearable
          style="width: 240px"
        />
        <el-select
          v-model="query.typeId"
          placeholder="活动类型"
          clearable
          style="width: 160px"
        >
          <el-option
            v-for="item in activityTypeList"
            :key="item.type_id"
            :label="item.type_name"
            :value="item.type_id"
          />
        </el-select>
        <el-select
          v-model="query.status"
          placeholder="活动状态"
          clearable
          style="width: 150px"
        >
          <el-option
            v-for="status in activityStatusOptions"
            :key="status"
            :label="status"
            :value="status"
          />
        </el-select>
        <el-button :icon="Search" @click="loadActivityList">查询</el-button>
      </div>

      <el-table
        :data="pageTableData"
        height="520"
        @row-click="handleRowClick"
      >
        <el-table-column prop="activity_name" label="活动名称" min-width="190" />
        <el-table-column prop="type_name" label="类型" width="110" />
        <el-table-column prop="org_name" label="主办组织" width="150" />
        <el-table-column prop="start_time" label="活动时间" width="170" />
        <el-table-column prop="location" label="地点" width="140" />

        <el-table-column label="剩余名额" width="100">
          <template #default="{ row }">
            {{ row.capacity - row.registered_count }}
          </template>
        </el-table-column>

        <el-table-column prop="base_score" label="基础分" width="90" />
        <el-table-column prop="activity_status" label="状态" width="110" />

        <el-table-column label="报名状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.registered ? 'success' : 'info'">
              {{ row.registered ? '已报名' : '未报名' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        layout="total, prev, pager, next"
        :total="activityTableData.length"
        style="margin-top: 14px"
      />
    </section>
  </AppLayout>
</template>

<script setup>
// 1. 导入模块（规范导入顺序：Vue内置API → 路由 → 图标 → 公共组件 → 接口请求）
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import http from '../api/http'

// 2. 常量配置抽离（魔法字符串统一管理，便于后续维护）
const PAGE_SIZE = 10
const ACTIVITY_STATUS_OPTIONS = ['DRAFT', 'OPEN', 'CLOSED', 'FINISHED', 'OFFLINE']

// 3. 响应式数据定义
// 路由实例，用于表格行点击后进入活动详情页
const router = useRouter()
// 活动类型下拉数据源
const activityTypeList = ref([])
// 后端返回全量活动表格数据（前端分页原始数据源）
const activityTableData = ref([])
// 当前页码
const currentPage = ref(1)
// 筛选查询参数
const query = reactive({
  keyword: '',
  typeId: null,
  status: ''
})

// 4. 前端分页计算属性
const pageTableData = computed(() => {
  const startIndex = (currentPage.value - 1) * PAGE_SIZE
  const endIndex = currentPage.value * PAGE_SIZE
  return activityTableData.value.slice(startIndex, endIndex)
})

// 5. 生命周期：页面初始化加载数据
onMounted(async () => {
  // 先加载活动类型下拉数据
  activityTypeList.value = await http.get('/activity-types')
  // 再加载活动列表
  await loadActivityList()
})

/**
 * 加载活动列表数据，筛选后重置页码到第一页
 * @description load函数逻辑
 */
const loadActivityList = async () => {
  activityTableData.value = await http.get('/activities', { params: query })
  currentPage.value = 1
}

/**
 * 表格行点击事件：跳转到活动详情页
 * @param {Object} row 当前点击行数据
 */
const handleRowClick = (row) => {
  router.push(`/activities/${row.activity_id}`)
}

const types = activityTypeList
const rows = activityTableData
const page = currentPage
const pageSize = PAGE_SIZE
const statuses = ACTIVITY_STATUS_OPTIONS
const pageRows = pageTableData
const load = loadActivityList
</script>
