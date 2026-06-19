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
import LeaderManage from '../views/LeaderManage.vue'
import AdminStudents from '../views/AdminStudents.vue'
import AdminOrganizations from '../views/AdminOrganizations.vue'
import AdminScores from '../views/AdminScores.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login },
    { path: '/', redirect: '/home' },
    { path: '/home', component: StudentHome },
    { path: '/activities', component: ActivityCenter },
    { path: '/activities/:id', component: ActivityDetail },
    { path: '/organizations', component: OrganizationCenter },
    { path: '/organizations/:id', component: OrganizationDetail },
    { path: '/mine', component: MyRecords },
    { path: '/ai', component: AiAssistant },
    { path: '/leader', component: LeaderManage, meta: { roles: ['ORG_LEADER', 'ADMIN'] } },
    { path: '/admin/students', component: AdminStudents, meta: { roles: ['ADMIN'] } },
    { path: '/admin/organizations', component: AdminOrganizations, meta: { roles: ['ADMIN'] } },
    { path: '/admin/scores', component: AdminScores, meta: { roles: ['ADMIN'] } }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) return '/login'
  if (to.path === '/login' && auth.token) return '/'
  if (to.meta.roles?.length && !to.meta.roles.some((role) => auth.roles.includes(role))) return '/home'
  return true
})

export default router
