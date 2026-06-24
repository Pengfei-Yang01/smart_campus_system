<template>
  <!-- 登录后的共享页面外壳。所有角色页面复用这里的侧边栏和顶部栏，
       slot 区域渲染每个页面自己的内容。 -->
  <div class="page">
    <aside class="sidebar">
      <div class="brand side-brand">
        <span class="brand-mark"><School /></span>
        <span>智慧校园综测</span>
      </div>

      <el-menu :default-active="$route.path" router>
        <!-- 学生菜单：学生首页以及通用浏览、个人记录页面。 -->
        <template v-if="auth.primaryRole === 'STUDENT'">
          <el-menu-item index="/home"><el-icon><House /></el-icon><span>学生首页</span></el-menu-item>
          <el-menu-item index="/activities"><el-icon><Calendar /></el-icon><span>活动中心</span></el-menu-item>
          <el-menu-item index="/organizations"><el-icon><OfficeBuilding /></el-icon><span>组织中心</span></el-menu-item>
          <el-menu-item index="/affairs"><el-icon><Document /></el-icon><span>事务申请</span></el-menu-item>
          <el-menu-item index="/messages"><el-icon><Bell /></el-icon><span>消息通知</span></el-menu-item>
          <el-menu-item index="/feedbacks"><el-icon><Star /></el-icon><span>评价反馈</span></el-menu-item>
          <el-menu-item index="/mine"><el-icon><TrendCharts /></el-icon><span>我的活动与积分</span></el-menu-item>
          <el-menu-item index="/ai"><el-icon><ChatLineRound /></el-icon><span>AI 助手</span></el-menu-item>
        </template>

        <!-- 组织负责人菜单：负责人管理页面以及通用页面。 -->
        <template v-else-if="auth.primaryRole === 'ORG_LEADER'">
          <el-menu-item index="/leader-home"><el-icon><House /></el-icon><span>负责人首页</span></el-menu-item>
          <el-menu-item index="/leader"><el-icon><Management /></el-icon><span>组织负责人管理</span></el-menu-item>
          <el-menu-item index="/activities"><el-icon><Calendar /></el-icon><span>活动浏览</span></el-menu-item>
          <el-menu-item index="/organizations"><el-icon><OfficeBuilding /></el-icon><span>组织浏览</span></el-menu-item>
          <el-menu-item index="/affairs"><el-icon><Document /></el-icon><span>事务申请</span></el-menu-item>
          <el-menu-item index="/messages"><el-icon><Bell /></el-icon><span>消息通知</span></el-menu-item>
          <el-menu-item index="/feedbacks"><el-icon><Star /></el-icon><span>评价反馈</span></el-menu-item>
          <el-menu-item index="/mine"><el-icon><TrendCharts /></el-icon><span>我的活动与积分</span></el-menu-item>
          <el-menu-item index="/ai"><el-icon><ChatLineRound /></el-icon><span>AI 助手</span></el-menu-item>
        </template>

        <!-- 管理员菜单：后台维护页面以及通用页面。 -->
        <template v-else>
          <el-menu-item index="/admin"><el-icon><House /></el-icon><span>管理员首页</span></el-menu-item>
          <el-menu-item index="/admin/students"><el-icon><User /></el-icon><span>学生管理</span></el-menu-item>
          <el-menu-item index="/admin/organizations"><el-icon><Checked /></el-icon><span>组织与审核</span></el-menu-item>
          <el-menu-item index="/admin/scores"><el-icon><Medal /></el-icon><span>积分审核</span></el-menu-item>
          <el-menu-item index="/activities"><el-icon><Calendar /></el-icon><span>活动总览</span></el-menu-item>
          <el-menu-item index="/organizations"><el-icon><OfficeBuilding /></el-icon><span>组织总览</span></el-menu-item>
          <el-menu-item index="/affairs"><el-icon><Document /></el-icon><span>事务审批</span></el-menu-item>
          <el-menu-item index="/messages"><el-icon><Bell /></el-icon><span>消息通知</span></el-menu-item>
          <el-menu-item index="/feedbacks"><el-icon><Star /></el-icon><span>评价反馈</span></el-menu-item>
          <el-menu-item index="/mine"><el-icon><TrendCharts /></el-icon><span>我的活动与积分</span></el-menu-item>
          <el-menu-item index="/ai"><el-icon><ChatLineRound /></el-icon><span>AI 助手</span></el-menu-item>
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
import {
  Calendar,
  Bell,
  ChatLineRound,
  Checked,
  Document,
  House,
  Management,
  Medal,
  OfficeBuilding,
  School,
  Star,
  SwitchButton,
  TrendCharts,
  User
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

defineProps({ title: String, subtitle: String })

const auth = useAuthStore()
const router = useRouter()

// 在顶部栏展示可读的角色标签。主角色同时控制
// 侧边栏渲染哪一组菜单。
const roleTag = computed(() => {
  if (auth.primaryRole === 'ADMIN') return { label: '系统管理员', type: 'danger' }
  if (auth.primaryRole === 'ORG_LEADER') return { label: '组织负责人', type: 'warning' }
  return { label: '普通学生', type: 'success' }
})

// 退出登录时通过状态仓库清空本地会话，然后回到登录页。
function logout() {
  auth.logout()
  router.push('/login')
}
</script>
