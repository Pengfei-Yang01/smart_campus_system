<template>
  <div class="login-wrap">
    <div class="login-card">
      <section class="login-side">
        <div class="brand">
          <span class="brand-mark"><School /></span>
          <span>智慧校园综测服务平台</span>
        </div>
        <h1>校园活动、组织治理与综测积分一体化管理</h1>
        <p>测试账号：admin / student1 / leader1，密码均为 123456。</p>
      </section>
      <section class="login-form">
        <el-tabs v-model="mode" stretch>
          <el-tab-pane label="登录" name="login">
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="账号">
                <el-input v-model="loginForm.username" placeholder="用户名或学号" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="loginForm.password" type="password" show-password />
              </el-form-item>
              <el-button type="primary" :loading="loading" style="width: 100%" @click="login">登录</el-button>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="注册" name="register">
            <el-form label-position="top" @submit.prevent>
              <div class="form-grid">
                <el-form-item label="用户名">
                  <el-input v-model="registerForm.username" />
                </el-form-item>
                <el-form-item label="学号">
                  <el-input v-model="registerForm.studentNo" />
                </el-form-item>
                <el-form-item label="姓名">
                  <el-input v-model="registerForm.realName" />
                </el-form-item>
                <el-form-item label="密码">
                  <el-input v-model="registerForm.password" type="password" show-password />
                </el-form-item>
                <el-form-item label="学院">
                  <el-input v-model="registerForm.college" />
                </el-form-item>
                <el-form-item label="专业">
                  <el-input v-model="registerForm.major" />
                </el-form-item>
                <el-form-item label="班级">
                  <el-input v-model="registerForm.className" />
                </el-form-item>
                <el-form-item label="年级">
                  <el-input v-model="registerForm.grade" />
                </el-form-item>
              </div>
              <el-button type="primary" :loading="loading" style="width: 100%" @click="register">注册并进入</el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { School } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const mode = ref('login')
const loading = ref(false)

const loginForm = reactive({ username: 'admin', password: '123456' })
const registerForm = reactive({
  username: '',
  studentNo: '',
  realName: '',
  password: '',
  college: '软件学院',
  major: '软件工程',
  className: '',
  grade: '2023'
})

async function login() {
  loading.value = true
  try {
    await auth.login(loginForm)
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}

async function register() {
  loading.value = true
  try {
    await auth.register(registerForm)
    ElMessage.success('注册成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>
