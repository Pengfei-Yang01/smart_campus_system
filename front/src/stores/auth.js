import { defineStore } from 'pinia'
import http from '../api/http'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    roles: (state) => state.user?.roles || [],
    isAdmin: (state) => state.user?.roles?.includes('ADMIN'),
    isLeader: (state) => state.user?.roles?.includes('ORG_LEADER'),
    primaryRole: (state) => {
      const roles = state.user?.roles || []
      if (roles.includes('ADMIN')) return 'ADMIN'
      if (roles.includes('ORG_LEADER')) return 'ORG_LEADER'
      return 'STUDENT'
    },
    homePath() {
      if (this.primaryRole === 'ADMIN') return '/admin'
      if (this.primaryRole === 'ORG_LEADER') return '/leader-home'
      return '/home'
    },
    isStudentPrimary() {
      return this.primaryRole === 'STUDENT'
    }
  },
  actions: {
    async login(payload) {
      const data = await http.post('/auth/login', payload)
      this.setSession(data)
    },
    async register(payload) {
      const data = await http.post('/auth/register', payload)
      this.setSession(data)
    },
    setSession(data) {
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
