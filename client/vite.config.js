import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },

  server: {
    port: 5173,
    proxy: {
      // '/api'로 시작하는 요청은 Spring(8080)으로 전달
      // 브라우저는 5173하고만 통신하므로 CORS가 발생하지 않음
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // '/metaverse'로 시작하는 요청은 메타버스 개발 서버(5174, base: '/metaverse/')로 전달
      // 같은 주소(5173) 아래에 있어야 로그인 쿠키가 공유되고, 나중에 앱(Capacitor) 안에서도 그대로 열린다
      '/metaverse': {
        target: 'http://localhost:5174',
        changeOrigin: true,
        ws: true, // 메타버스 코드 수정 시 자동 반영(HMR) 웹소켓
      },
    },
  },
})
