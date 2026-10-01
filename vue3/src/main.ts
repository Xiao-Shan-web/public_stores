import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// Font Awesome 图标
import '@fortawesome/fontawesome-free/css/all.min.css'
// 全局样式
import './static/css/variables.css'
import './static/css/main.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
