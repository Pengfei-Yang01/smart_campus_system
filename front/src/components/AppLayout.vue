<template>
  <div class="page">
    <aside class="sidebar">
      <div class="brand side-brand">
        <span class="brand-mark"><School /></span>
        <span>智慧校园综测</span>
      </div>
      <el-menu :default-active="$route.path" router>
        <template v-if="auth.primaryRole === 'STUDENT'">
          <el-menu-item index="/home"><el-icon><House /></el-icon><span>学生首页</span></el-menu-item>
          <el-menu-item index="/activities"><el-icon><Calendar /></el-icon><span>活动中心</span></el-menu-item>
          <el-menu-item index="/organizations"><el-icon><OfficeBuilding /></el-icon><span>组织中心</span></el-menu-item>
          <el-menu-item index="/mine"><el-icon><TrendCharts /></el-icon><span>我的活动与积分</span></el-menu-item>
          <el-menu-item index="/ai"><el-icon><ChatLineRound /></el-icon><span>AI 助手</span></el-menu-item>
        </template>

        <template v-else-if="auth.primaryRole === 'ORG_LEADER'">
          <el-menu-item index="/leader-home"><el-icon><House /></el-icon><span>负责人首页</span></el-menu-item>
          <el-menu-item index="/leader"><el-icon><Management /></el-icon><span>组织负责人管理</span></el-menu-item>
          <el-menu-item index="/activities"><el-icon><Calendar /></el-icon><span>活动浏览</span></el-menu-item>
          <el-menu-item index="/organizations"><el-icon><OfficeBuilding /></el-icon><span>组织浏览</span></el-menu-item>
        </template>

        <template v-else>
          <el-menu-item index="/admin"><el-icon><House /></el-icon><span>管理员首页</span></el-menu-item>
          <el-menu-item index="/admin/students"><el-icon><User /></el-icon><span>学生管理</span></el-menu-item>
          <el-menu-item index="/admin/organizations"><el-icon><Checked /></el-icon><span>组织与审核</span></el-menu-item>
          <el-menu-item index="/admin/scores"><el-icon><Medal /></el-icon><span>积分审核</span></el-menu-item>
          <el-menu-item index="/activities"><el-icon><Calendar /></el-icon><span>活动总览</span></el-menu-item>
          <el-menu-item index="/organizations"><el-icon><OfficeBuilding /></el-icon><span>组织总览</span></el-menu-item>
        </template>
      </el-menu>
    </aside>
    <main class="main">
      <header class="topbar">
        <div>
          <h1 class="page-title">{{ title }}</h1>
          <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
        </div>
        <div class="toolbar">
          <el-tag>{{ auth.user?.realName }}</el-tag>
          <el-tag :type="roleTag.type">{{ roleTag.label }}</el-tag>
          <el-button :icon="SwitchButton" @click="logout">退出</el-button>
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Calendar, ChatLineRound, Checked, House, Management, Medal, OfficeBuilding, School, SwitchButton, TrendCharts, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

defineProps({ title: String, subtitle: String })
const auth = useAuthStore()
const router = useRouter()

const roleTag = computed(() => {
  if (auth.primaryRole === 'ADMIN') return { label: '系统管理员', type: 'danger' }
  if (auth.primaryRole === 'ORG_LEADER') return { label: '组织负责人', type: 'warning' }
  return { label: '普通学生', type: 'success' }
})

function logout() {
  auth.logout()
  router.push('/login')
}
</script>
