import { defineStore } from 'pinia'
import http from '../api/http'

// 集中管理认证和会话状态，并把令牌与用户信息同步到浏览器本地存储，
// 刷新页面后仍能保持登录状态。
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    // 后端当前在令牌中返回一个主角色，但前端
    // 仍保留数组形式的读取器，方便后续扩展多角色模型。
    roles: (state) => state.user?.roles || [],
    isAdmin: (state) => state.user?.roles?.includes('ADMIN'),
    isLeader: (state) => state.user?.roles?.includes('ORG_LEADER'),
    // 选择一个角色决定首页和侧边栏，优先级与后端保持一致。
    primaryRole: (state) => {
      const roles = state.user?.roles || []
      if (roles.includes('ADMIN')) return 'ADMIN'
      if (roles.includes('ORG_LEADER')) return 'ORG_LEADER'
      return 'STUDENT'
    },
    // 登录后和受保护路由回退时使用的角色专属首页。
    homePath() {
      if (this.primaryRole === 'ADMIN') return '/admin'
      if (this.primaryRole === 'ORG_LEADER') return '/leader-home'
      return '/home'
    },
    // 用于判断页面是否应该显示学生专属操作，例如
    // 申请成为组织负责人。
    isStudentPrimary() {
      return this.primaryRole === 'STUDENT'
    }
  },
  actions: {
    // 登录并持久化后端返回的令牌和用户快照。
    async login(payload) {
      const data = await http.post('/auth/login', payload)
      this.setSession(data)
    },
    // 注册学生账号；后端返回与登录相同的会话数据。
    async register(payload) {
      const data = await http.post('/auth/register', payload)
      this.setSession(data)
    },
    // 统一在这里更新响应式状态和浏览器持久化数据。
    setSession(data) {
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    // 清空所有会话数据，调用方随后跳转到登录页。
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
