import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  // sockjs-client 是 Node 风格包，预构建产物引用 Node 全局 global，
  // 浏览器无该标识符会抛 ReferenceError 导致客服页路由导航失败（白屏），
  // 编译期将其替换为 globalThis 修复
  define: {
    global: 'globalThis'
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5174,
    // 开发环境代理：/api 请求转发到后端服务
    proxy: {
      '/api': {
        target: 'http://localhost:8089',
        changeOrigin: true
      },
      // 用户上传的文件（头像等），由后端静态资源映射提供
      '/uploads': {
        target: 'http://localhost:8089',
        changeOrigin: true
      },
      // WebSocket 端点（SockJS + STOMP）
      '/ws': {
        target: 'http://localhost:8089',
        changeOrigin: true,
        ws: true
      }
    }
  }
})
