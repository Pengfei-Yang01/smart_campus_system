import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

// 应用启动流程：
// 1. 从根组件创建前端应用。
// 2. 安装状态管理，用来保存登录和会话状态。
// 3. 安装路由系统，用来管理按角色划分的页面。
// 4. 安装界面组件库。
// 5. 把应用挂载到页面入口节点。
createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
