<template>
  <div class="page">
    <aside class="sidebar">
      <div class="brand side-brand">
        <span class="brand-mark"><School /></span>
        <span>智慧校园综测</span>
      </div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/home"><el-icon><House /></el-icon><span>学生首页</span></el-menu-item>
        <el-menu-item index="/activities"><el-icon><Calendar /></el-icon><span>活动中心</span></el-menu-item>
        <el-menu-item index="/organizations"><el-icon><OfficeBuilding /></el-icon><span>组织中心</span></el-menu-item>
        <el-menu-item index="/mine"><el-icon><TrendCharts /></el-icon><span>我的活动与积分</span></el-menu-item>
        <el-menu-item index="/ai"><el-icon><ChatLineRound /></el-icon><span>AI 助手</span></el-menu-item>
        <el-menu-item v-if="auth.isLeader || auth.isAdmin" index="/leader"><el-icon><Management /></el-icon><span>负责人管理</span></el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin/students"><el-icon><User /></el-icon><span>学生管理</span></el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin/organizations"><el-icon><Checked /></el-icon><span>组织与审核</span></el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin/scores"><el-icon><Medal /></el-icon><span>积分审核</span></el-menu-item>
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
          <el-tag v-for="role in auth.roles" :key="role" type="info">{{ role }}</el-tag>
          <el-button :icon="SwitchButton" @click="logout">退出</el-button>
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Calendar, ChatLineRound, Checked, House, Management, Medal, OfficeBuilding, School, SwitchButton, TrendCharts, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

defineProps({ title: String, subtitle: String })
const auth = useAuthStore()
const router = useRouter()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>
