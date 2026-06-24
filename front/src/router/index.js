import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import Login from '../views/Login.vue'
import StudentHome from '../views/StudentHome.vue'
import ActivityCenter from '../views/ActivityCenter.vue'
import ActivityDetail from '../views/ActivityDetail.vue'
import OrganizationCenter from '../views/OrganizationCenter.vue'
import OrganizationDetail from '../views/OrganizationDetail.vue'
import MyRecords from '../views/MyRecords.vue'
import AiAssistant from '../views/AiAssistant.vue'
import AffairApplications from '../views/AffairApplications.vue'
import MessageCenter from '../views/MessageCenter.vue'
import ActivityFeedback from '../views/ActivityFeedback.vue'
import LeaderHome from '../views/LeaderHome.vue'
import LeaderManage from '../views/LeaderManage.vue'
import AdminDashboard from '../views/AdminDashboard.vue'
import AdminStudents from '../views/AdminStudents.vue'
import AdminOrganizations from '../views/AdminOrganizations.vue'
import AdminScores from '../views/AdminScores.vue'

// 整个前端的路由表。公开页面保持最少，其余
// 业务页面都由下面的导航守卫保护。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login },
    { path: '/', redirect: () => useAuthStore().homePath },
    { path: '/home', component: StudentHome, meta: { primaryRoles: ['STUDENT'] } },
    { path: '/activities', component: ActivityCenter },
    { path: '/activities/:id', component: ActivityDetail },
    { path: '/organizations', component: OrganizationCenter },
    { path: '/organizations/:id', component: OrganizationDetail },
    { path: '/mine', component: MyRecords },
    { path: '/ai', component: AiAssistant },
    { path: '/affairs', component: AffairApplications },
    { path: '/messages', component: MessageCenter },
    { path: '/feedbacks', component: ActivityFeedback },
    { path: '/leader-home', component: LeaderHome, meta: { primaryRoles: ['ORG_LEADER'] } },
    { path: '/leader', component: LeaderManage, meta: { primaryRoles: ['ORG_LEADER'] } },
    { path: '/admin', component: AdminDashboard, meta: { primaryRoles: ['ADMIN'] } },
    { path: '/admin/students', component: AdminStudents, meta: { roles: ['ADMIN'] } },
    { path: '/admin/organizations', component: AdminOrganizations, meta: { roles: ['ADMIN'] } },
    { path: '/admin/scores', component: AdminScores, meta: { roles: ['ADMIN'] } }
  ]
})

// 全局导航守卫：
// - 未登录用户只能打开登录页
// - 已登录用户访问登录页时会被重定向
// - 角色受限页面会回退到当前用户自己的首页
// - 主角色配置用于限制只有对应主角色可以访问的首页
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) return '/login'
  if (to.path === '/login' && auth.token) return auth.homePath
  if (to.meta.roles?.length && !to.meta.roles.some((role) => auth.roles.includes(role))) return auth.homePath
  if (to.meta.primaryRoles?.length && !to.meta.primaryRoles.includes(auth.primaryRole)) return auth.homePath
  return true
})

export default router
