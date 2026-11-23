<script setup>
// 예약 사이트에서 돌아왔을 때 "○○캠핑장 예약하셨나요?" 팝업 (웹 · 앱 같이 씀, App.vue 에 한 번만 둔다)
// 기록: stores/reservationReturn (캠핑장 상세 · 내 예약 카드의 "예약 사이트 ↗"가 남김)
// 돌아온 것 알아채기: 새 탭으로 갔다 오면 visibilitychange, 같은 탭에서 뒤로 오면 화면을 다시 열 때(onMounted)
// 예약했어요 → 예약 정보 확인 화면 (로그인 안 했으면 로그인부터, 로그인 뒤 그 화면으로 이어짐)
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WebConfirmModal from '@/components/web/WebConfirmModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useReservationReturnStore } from '@/stores/reservationReturn'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const visit = useReservationReturnStore()

const isWeb = computed(() => route.path.startsWith('/web'))
// 예약 확인 · 로그인 화면에서는 띄우지 않는다 (이미 그 일을 하는 중)
const open = computed(() => !!visit.asking && !/booking-check|login/.test(route.path))
// 받침이 있으면 '을', 없으면 '를' (예: 내린천 글램핑을 · 설하캠프를). 한글이 아니면 '을(를)'
function eulReul(word) {
  const c = word.charCodeAt(word.length - 1) - 0xac00
  if (c < 0 || c > 11171) return '을(를)'
  return c % 28 ? '을' : '를'
}
const title = computed(() => {
  const name = visit.asking?.campName || '이 캠핑장'
  return `${name}${eulReul(name)} 예약하셨나요?`
})

function onVisible() {
  if (document.visibilityState === 'visible') visit.check()
}
onMounted(() => {
  visit.check()
  document.addEventListener('visibilitychange', onVisible)
})
onBeforeUnmount(() => document.removeEventListener('visibilitychange', onVisible))

function yes() {
  const v = visit.asking
  visit.clear()
  const base = isWeb.value ? '/web' : '/app'
  const target = `${base}/booking-check?camp=${v.campId}${v.planId ? `&plan=${v.planId}` : ''}`
  // 내 예약은 계정에 저장되므로 로그인부터. next: 로그인 뒤 돌아올 주소
  if (!auth.isLoggedIn) router.push({ path: `${base}/login`, query: { next: target } })
  else router.push(target)
}
const no = () => visit.clear()
</script>

<template>
  <WebConfirmModal v-if="isWeb" :open="open" :title="title" confirm-label="예약했어요" cancel-label="아직 안 했어요"
    :danger="false" @confirm="yes" @cancel="no" />
  <div v-else-if="open" class="dim" role="alertdialog" aria-modal="true" aria-labelledby="resv-return-title" @click.self="no">
    <div class="box">
      <h2 id="resv-return-title">{{ title }}</h2>
      <div class="actions">
        <button type="button" class="cancel" @click="no">아직 안 했어요</button>
        <button type="button" class="ok" @click="yes">예약했어요</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 앱(모바일) 팝업: 색은 tokens.css 변수만 */
.dim {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--color-nudge-dim);
}
.box {
  width: 320px;
  max-width: 100%;
  padding: 24px 20px 18px;
  border-radius: 18px;
  background: var(--color-bg);
  text-align: center;
}
h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
  word-break: keep-all;
}
.actions {
  margin-top: 18px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.actions button {
  height: 46px;
  border-radius: 12px;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.cancel {
  border: 1px solid var(--color-line);
  background: var(--color-bg);
  color: var(--color-text);
}
.ok {
  border: 0;
  background: var(--color-primary);
  color: var(--color-bg);
}
</style>
