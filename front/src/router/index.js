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
import LeaderHome from '../views/LeaderHome.vue'
import LeaderManage from '../views/LeaderManage.vue'
import AdminDashboard from '../views/AdminDashboard.vue'
import AdminStudents from '../views/AdminStudents.vue'
import AdminOrganizations from '../views/AdminOrganizations.vue'
import AdminScores from '../views/AdminScores.vue'

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
    { path: '/leader-home', component: LeaderHome, meta: { primaryRoles: ['ORG_LEADER'] } },
    { path: '/leader', component: LeaderManage, meta: { primaryRoles: ['ORG_LEADER'] } },
    { path: '/admin', component: AdminDashboard, meta: { primaryRoles: ['ADMIN'] } },
    { path: '/admin/students', component: AdminStudents, meta: { roles: ['ADMIN'] } },
    { path: '/admin/organizations', component: AdminOrganizations, meta: { roles: ['ADMIN'] } },
    { path: '/admin/scores', component: AdminScores, meta: { roles: ['ADMIN'] } }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) return '/login'
  if (to.path === '/login' && auth.token) return auth.homePath
  if (to.meta.roles?.length && !to.meta.roles.some((role) => auth.roles.includes(role))) return auth.homePath
  if (to.meta.primaryRoles?.length && !to.meta.primaryRoles.includes(auth.primaryRole)) return auth.homePath
  return true
})

export default router
