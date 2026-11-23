<script setup>
// 전체 메뉴 (설계 ⑩): 헤더의 햄버거 버튼을 누르면 오른쪽에서 열린다
// 아직 웹 화면이 없는 항목은 눌러도 이동하지 않는다 (href="#")
import { computed } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { DISASTER_PATH, METAVERSE_PATH } from '@/utils/webLinks'

defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close'])

const auth = useAuthStore()
const router = useRouter()
const memberName = computed(() => auth.member?.name ?? '')

async function logout() {
  await auth.logout()
  emit('close')
  router.push('/web')
}
</script>

<template>
  <template v-if="open">
    <div class="dim" aria-hidden="true" @click="emit('close')"></div>
    <aside class="drawer" role="dialog" aria-modal="true" aria-label="전체 메뉴">
      <div class="top">
        <div v-if="auth.isLoggedIn" class="welcome">
          <span class="hello">환영합니다 {{ memberName }}님</span>
          <RouterLink to="/web/my-info" class="edit" @click="emit('close')">내 정보 ›</RouterLink>
        </div>
        <div v-else class="auth">
          <RouterLink to="/web/login" class="btn-login">로그인</RouterLink>
          <RouterLink to="/web/signup" class="btn-signup">회원가입</RouterLink>
        </div>
        <button type="button" class="icon-btn" aria-label="메뉴 닫기" @click="emit('close')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
        </button>
      </div>

      <div class="body">
        <span class="group">내 활동</span>
        <RouterLink to="/web/my-booking" class="row strong" @click="emit('close')"><span>내 캠핑</span><i class="arrow"></i></RouterLink>
        <RouterLink to="/web/my-info" class="row" @click="emit('close')"><span>내 정보</span><i class="arrow"></i></RouterLink>
        <RouterLink to="/web/saved" class="row"><span>찜한 캠핑장</span><i class="arrow"></i></RouterLink>
        <RouterLink to="/web/survey" class="row"><span>내 캠핑 유형</span><span class="sub">저장된 검사 결과 ›</span></RouterLink>

        <span class="badge">안전</span>
        <RouterLink to="/web/family" class="row" @click="emit('close')"><span>모임방</span><span class="sub">함께 가는 사람과 안전 공유 ›</span></RouterLink>
        <RouterLink :to="METAVERSE_PATH" class="row" @click="emit('close')"><span>메타버스 안전 탐험</span><i class="arrow"></i></RouterLink>
        <RouterLink :to="DISASTER_PATH" class="row" @click="emit('close')"><span>3D 재난 대피 지도</span><i class="arrow"></i></RouterLink>
        <RouterLink to="/web/my-booking" class="row" @click="emit('close')"><span>안전 점검 체크리스트</span><span class="sub">내 캠핑에서 열려요 ›</span></RouterLink>
      </div>

      <div class="bottom">
        <RouterLink to="/web/alerts" @click="emit('close')">알림</RouterLink>
        <a v-if="auth.isLoggedIn" href="#" class="logout" @click.prevent="logout">로그아웃</a>
      </div>
    </aside>
  </template>
</template>

<style scoped>
.dim {
  position: fixed;
  inset: 0;
  z-index: 25;
  background: var(--web-dim-soft);
}
.drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 26;
  width: 440px;
  max-width: 100%;
  background: var(--web-bg);
  box-shadow: -12px 0 40px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.top {
  flex: none;
  height: 100px;
  padding: 0 28px 0 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--web-line);
}
.welcome {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hello {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.4px;
}
.edit {
  font-size: 14px;
  color: var(--web-text-sub);
}
.auth {
  display: flex;
  gap: 8px;
}
.btn-login,
.btn-signup {
  height: 44px;
  padding: 0 22px;
  border-radius: 999px;
  font-size: 15px;
  display: flex;
  align-items: center;
}
.btn-login {
  background: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
.btn-signup {
  border: 1px solid var(--web-line-ghost);
  color: var(--web-text);
}
.icon-btn {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  border: 1px solid var(--web-line-btn);
  background: var(--web-bg);
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.body {
  flex: 1;
  padding: 22px 36px 0;
  display: flex;
  flex-direction: column;
}
.group {
  font-size: 13px;
  font-weight: 700;
  color: var(--web-text-sub);
}
.badge {
  margin-top: 26px;
  align-self: flex-start;
  padding: 3px 12px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 13px;
  font-weight: 700;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 0;
  border-bottom: 1px solid var(--web-line);
  color: var(--web-text);
  font-size: 16px;
}
.row.strong {
  font-weight: 700;
  color: var(--web-green);
}
.sub {
  font-size: 13px;
  color: var(--web-text-sub);
}
/* 오른쪽 화살표 (›) */
.arrow {
  width: 8px;
  height: 8px;
  margin-right: 4px;
  border-top: 2px solid var(--web-arrow);
  border-right: 2px solid var(--web-arrow);
  transform: rotate(45deg);
}
.bottom {
  flex: none;
  padding: 20px 36px 32px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  font-size: 15px;
  border-top: 1px solid var(--web-line);
  color: var(--web-text-sub);
}
.logout {
  color: var(--web-terracotta);
}
</style>
