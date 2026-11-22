import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  // client(5173)가 /metaverse 로 들어온 요청을 여기(5174)로 넘겨 준다 → 주소가 하나라 로그인 쿠키·/api 를 같이 쓴다
  // 그래서 public/ 파일은 '/data/...' 가 아니라 `${import.meta.env.BASE_URL}data/...` 로 불러야 한다
  base: '/metaverse/',
  server: {
    port: 5174,
    strictPort: true,
    // 메타버스만 따로 띄웠을 때(localhost:5174/metaverse/metaverse)도 퀴즈를 DB에서 가져오도록
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
