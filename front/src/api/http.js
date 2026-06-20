import axios from 'axios'
import { ElMessage } from 'element-plus'

// 共享请求实例。开发环境中前端开发服务会把接口请求代理到后端，
// 生产部署也可以继续使用相同的相对前缀。
const http = axios.create({
  baseURL: '/api',
  timeout: 12000
})

// 给每个受保护接口请求附加已保存的令牌，后端拦截器会读取这个认证请求头。
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 统一处理后端响应格式。控制器返回统一包装对象，页面组件成功时
// 可以直接使用响应数据，并在失败时显示统一的错误提示。
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (error) => {
    ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default http
