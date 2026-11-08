<script setup>
// 내 캠핑 탭 첫 화면. 원래 목적은 예약한 캠핑 정보(일정·날씨·위험 알림·재난지도)를 보여 주는 것
// 지금은 캠핑장 상세에서 "내 캠핑에 일정 등록"한 캠핑을 카드로 보여 준다 (서버 저장, stores/myCamping.js)
// 날씨·위험 알림은 데이터가 아직 없어 비워 둔다 (수치를 지어내지 않기 위해)
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useMyCampingStore } from '@/stores/myCamping'
import { labelWithDay, nightsBetween, todayIso } from '@/utils/date'
import BottomNav from '@/components/common/BottomNav.vue'

const auth = useAuthStore()
const myCamping = useMyCampingStore()

const error = ref('')

async function load() {
  error.value = ''
  if (!auth.isLoggedIn) return
  try {
    await myCamping.load()
  } catch (e) {
    error.value = e.message
  }
}
onMounted(load)

// 카드에 보일 글: 날짜 범위 · 몇 박, 그리고 D-day
const cards = computed(() => {
  const today = todayIso()
  return myCamping.plans.map((p) => {
    let badge
    if (p.checkOut < today) badge = '지난 캠핑'
    else if (p.checkIn <= today) badge = '캠핑 중'
    else badge = `D-${nightsBetween(today, p.checkIn)}`
    const nights = nightsBetween(p.checkIn, p.checkOut)
    return {
      ...p,
      badge,
      past: p.checkOut < today,
      range: `${labelWithDay(p.checkIn)} – ${labelWithDay(p.checkOut)} · ${nights}박`,
    }
  })
})

async function remove(p) {
  if (!window.confirm(`'${p.campName}' 일정을 지울까요?`)) return
  try {
    await myCamping.remove(p.id)
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div class="page">
    <div class="mycamp">
      <h1>내 캠핑</h1>

      <div v-if="!auth.isLoggedIn" class="empty">
        <p>내 캠핑은 로그인하면 쓸 수 있어요.</p>
        <RouterLink to="/login" class="primary">로그인</RouterLink>
      </div>

      <template v-else>
        <!-- 등록한 캠핑 카드. 날씨·위험 알림은 데이터 연결 후 -->
        <p v-if="error" class="placeholder">
          {{ error }} <button type="button" class="retry" @click="load">다시 시도</button>
        </p>
        <ul v-else-if="cards.length" class="plans">
          <li v-for="p in cards" :key="p.id" class="plan" :class="{ past: p.past }">
            <div class="plan-head">
              <span class="badge">{{ p.badge }}</span>
              <button type="button" class="del" :aria-label="`${p.campName} 일정 지우기`" @click="remove(p)">삭제</button>
            </div>
            <RouterLink :to="`/camps/${p.campId}`" class="plan-name">{{ p.campName }}</RouterLink>
            <p class="plan-place">{{ p.place }}</p>
            <p class="plan-range">{{ p.range }}</p>
            <p v-if="p.memo" class="plan-memo">{{ p.memo }}</p>
          </li>
        </ul>
        <p v-else class="placeholder">
          등록한 캠핑이 없어요.<br />캠핑장 상세에서 '내 캠핑에 일정 등록'을 눌러 보세요.
        </p>

        <RouterLink to="/my-camping/family" class="family-btn">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="9" cy="8" r="3" />
            <circle cx="17" cy="9" r="2.5" />
            <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M15 14.5c.6-.3 1.3-.5 2-.5 2.8 0 4.5 2.2 4.5 5" />
          </svg>
          <span class="text">
            <b>가족 모임방</b>
            <small>모일 곳 · 아이 카드 · 일정을 가족과 함께 봐요</small>
          </span>
          <span aria-hidden="true">›</span>
        </RouterLink>
      </template>
    </div>
    <BottomNav active="mycamp" />
  </div>
</template>

<style scoped>
/* 화면 전체 틀: 위는 내용(스크롤), 맨 아래는 하단바 (탐색 화면과 같은 방식) */
.page {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.mycamp {
  flex: 1;
  min-height: 0; /* 이게 없으면 내용이 길 때 하단바가 아래로 밀려난다 */
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px 20px;
}
h1 {
  margin: 0;
  font-size: 32px;
  font-weight: 900;
  letter-spacing: -0.03em;
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin: 48px 0 0;
  font-size: 15px;
  color: var(--color-text-sub);
}
.empty p {
  margin: 0;
}
.primary {
  height: 48px;
  padding: 0 20px;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-card);
  display: inline-flex;
  align-items: center;
  font-size: 15px;
  font-weight: 700;
}
.placeholder {
  margin: 0;
  padding: 24px 16px;
  border-radius: 14px;
  background: var(--color-bg-soft);
  text-align: center;
  font-size: 14px;
  color: var(--color-text-sub);
}
.retry {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  color: var(--color-text);
  text-decoration: underline;
  cursor: pointer;
}
.plans {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.plan {
  padding: 16px;
  border-radius: 14px;
  background: var(--color-bg-soft);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.plan.past {
  opacity: 0.6;
}
.plan-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.badge {
  padding: 3px 10px;
  border-radius: 12px;
  background: var(--color-text);
  color: var(--color-card);
  font-size: 12px;
  font-weight: 700;
}
.del {
  padding: 4px 0 4px 12px;
  border: none;
  background: none;
  font: inherit;
  font-size: 13px;
  color: var(--color-text-sub);
  cursor: pointer;
}
.plan-name {
  font-size: 19px;
  font-weight: 900;
  color: var(--color-text);
}
.plan-place,
.plan-memo {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-sub);
}
.plan-range {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}
.family-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
}
.family-btn .text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.family-btn b {
  font-size: 16px;
}
.family-btn small {
  font-size: 13px;
  color: var(--color-text-sub);
}
</style>
