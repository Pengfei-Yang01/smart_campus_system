<template>
  <!-- 登录/注册页，是唯一无需登录即可访问的路由，
       后端响应成功后通过 auth store 创建会话。 -->
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
            <el-form label-position="top" @submit.prevent="login">
              <el-form-item label="账号">
                <el-input v-model="loginForm.username" placeholder="用户名或学号" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="loginForm.password" type="password" show-password @input="loginForm.password = noChinese($event)" />
              </el-form-item>
              <el-button native-type="submit" type="primary" :loading="loading" style="width: 100%">登录</el-button>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="注册" name="register">
            <el-form label-position="top" @submit.prevent="register">
              <div class="form-grid">
                <el-form-item label="用户名">
                  <el-input v-model="registerForm.username" />
                </el-form-item>
                <el-form-item label="学号">
                  <el-input v-model="registerForm.studentNo" @input="registerForm.studentNo = alphanumeric($event)" />
                </el-form-item>
                <el-form-item label="姓名">
                  <el-input v-model="registerForm.realName" @input="registerForm.realName = noSpace($event)" />
                </el-form-item>
                <el-form-item label="密码">
                  <el-input v-model="registerForm.password" type="password" show-password @input="registerForm.password = noChinese($event)" />
                </el-form-item>
                <el-form-item label="学院">
                  <el-input v-model="registerForm.college" />
                </el-form-item>
                <el-form-item label="专业">
                  <el-input v-model="registerForm.major" />
                </el-form-item>
                <el-form-item label="班级">
                  <el-input v-model="registerForm.className" @input="registerForm.className = digits($event)" />
                </el-form-item>
                <el-form-item label="年级">
                  <el-select v-model="registerForm.grade" placeholder="请选择年级">
                    <el-option v-for="y in years" :key="y" :label="y" :value="y" />
                  </el-select>
                </el-form-item>
              </div>
              <el-button native-type="submit" type="primary" :loading="loading" style="width: 100%">注册并进入</el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
  </div>
</template>

<script setup>
// 规范导入顺序：Vue内置API → 路由 → ElementPlus工具类 → 图标 → Pinia状态
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { School } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

// 常量抽离
const DEFAULT_TAB = 'login'
const DEFAULT_USER = 'admin'
const DEFAULT_PWD = '123456'
const DEFAULT_COLLEGE = '软件学院'
const DEFAULT_MAJOR = '软件工程'
const DEFAULT_GRADE = '2023'

// 输入过滤函数
const noChinese = (v) => v.replace(/[一-鿿\s]/g, '')
const alphanumeric = (v) => v.replace(/[^\w]/g, '')
const digits = (v) => v.replace(/\D/g, '')
const noSpace = (v) => v.replace(/\s/g, '')

// 年级选项
const years = Array.from({ length: 8 }, (_, i) => String(2020 + i))

// 实例初始化
const authStore = useAuthStore()
const router = useRouter()

// 语义化命名响应式变量
const currentTabMode = ref(DEFAULT_TAB)
const submitLoading = ref(false)

// 登录表单
const loginFormData = reactive({
  username: DEFAULT_USER,
  password: DEFAULT_PWD
})

// 注册表单
const registerFormData = reactive({
  username: '',
  studentNo: '',
  realName: '',
  password: '',
  college: DEFAULT_COLLEGE,
  major: DEFAULT_MAJOR,
  className: '',
  grade: DEFAULT_GRADE
})

/**
 * 登录逻辑
 */
const handleLogin = async () => {
  submitLoading.value = true
  try {
    await authStore.login(loginFormData)
    ElMessage.success('登录成功')
    router.push(authStore.homePath)
  } finally {
    submitLoading.value = false
  }
}

/**
 * 注册逻辑
 */
const handleRegister = async () => {
  submitLoading.value = true
  try {
    await authStore.register(registerFormData)
    ElMessage.success('注册成功')
    router.push(authStore.homePath)
  } finally {
    submitLoading.value = false
  }
}

const mode = currentTabMode
const loading = submitLoading
const loginForm = loginFormData
const registerForm = registerFormData
const auth = authStore
const login = handleLogin
const register = handleRegister
</script>