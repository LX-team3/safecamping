<script setup>
// 가족방 · 일정 탭. 가족이 함께 보는 일정 (예: 06:00 출발, 15:00 체크인). 시간 순, 날짜별로 묶어 보여 준다
import { ref, computed, onMounted } from 'vue'
import { useFamilyStore } from '@/stores/family'
import { askConfirm } from '@/utils/confirm'
import MobileAddButton from './MobileAddButton.vue'

const family = useFamilyStore()

const adding = ref(false)
const title = ref('')
const startAt = ref('') // <input type="datetime-local"> 값: "2026-11-14T06:00" (내 기기 시간대)
const memo = ref('')
const formError = ref('')
const error = ref('')

onMounted(async () => {
  try {
    await family.fetchSchedules()
  } catch (e) {
    error.value = e.message
  }
})

// 입력칸 닫기 (뒤로): 쓰던 내용과 오류 문구도 비운다
function closeForm() {
  adding.value = false
  title.value = ''
  startAt.value = ''
  memo.value = ''
  formError.value = ''
}

async function add() {
  if (!title.value) {
    formError.value = '일정 내용을 입력해 주세요.'
    return
  }
  if (!startAt.value) {
    formError.value = '날짜와 시간을 골라 주세요.'
    return
  }
  try {
    // 기기 시간 그대로 Date 로 읽고 ISO(UTC, 끝에 Z) 로 보낸다 → 서버는 시간대가 붙은 값으로 저장
    await family.addSchedule({ title: title.value, startAt: new Date(startAt.value).toISOString(), memo: memo.value })
    adding.value = false
    title.value = ''
    startAt.value = ''
    memo.value = ''
    formError.value = ''
  } catch (e) {
    formError.value = e.message
  }
}

async function remove(s) {
  if (!(await askConfirm({ title: '일정을 지울까요?', message: s.title, confirmLabel: '지우기' }))) return
  try {
    await family.deleteSchedule(s.id)
  } catch (e) {
    error.value = e.message
  }
}

// 화면에는 한국 시간으로 보여 준다 (db/CLAUDE.md: 표시할 때만 KST)
const KST = { timeZone: 'Asia/Seoul' }
function dayLabel(iso) {
  return new Date(iso).toLocaleDateString('ko-KR', { ...KST, month: 'numeric', day: 'numeric', weekday: 'short' })
}
function timeLabel(iso) {
  return new Date(iso).toLocaleTimeString('ko-KR', { ...KST, hour: '2-digit', minute: '2-digit', hour12: false })
}

// 같은 날짜끼리 묶는다: [{ day: '11. 14. (토)', items: [...] }]
const groups = computed(() => {
  const out = []
  for (const s of family.schedules) {
    const day = dayLabel(s.startAt)
    if (out.length && out[out.length - 1].day === day) out[out.length - 1].items.push(s)
    else out.push({ day, items: [s] })
  }
  return out
})
</script>

<template>
  <!-- 입력 중: 왼쪽 "뒤로" (입력칸을 닫고 목록·빈 카드로). + 를 다시 눌러 닫는 것보다 알아보기 쉬워서 -->
  <div v-if="adding" class="tab-head">
    <button type="button" class="form-back" @click="closeForm">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
        stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
      뒤로
    </button>
  </div>
  <!-- + 버튼 줄: 일정이 있을 때만 (비었을 때는 아래 빈 카드의 "일정 등록" 버튼이 대신). 안내 문장은 빈 카드와 겹쳐 뺐다 -->
  <div v-else-if="family.schedules.length" class="tab-head end">
    <MobileAddButton label="일정 추가" @click="adding = true" />
  </div>

  <form v-if="adding" class="stack-form" @submit.prevent="add">
    <input v-model.trim="title" maxlength="50" placeholder="일정 (예: 집에서 출발)" />
    <input v-model="startAt" type="datetime-local" aria-label="날짜와 시간" />
    <input v-model.trim="memo" maxlength="200" placeholder="메모 (선택)" />
    <button type="submit" class="primary">추가</button>
    <p v-if="formError" class="error" role="alert">{{ formError }}</p>
  </form>

  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <!-- 비었을 때: 문장 하나 → 카드 (웹 저장 화면 빈 카드와 같은 모양) + 바로 추가하는 버튼 -->
  <div v-if="!family.schedules.length && !adding" class="empty-card">
    <span class="empty-icon" aria-hidden="true">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
        stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4M16 3v4" /></svg>
    </span>
    <b>등록한 일정이 없습니다</b>
    <p>일정을 등록하세요</p>
    <button type="button" class="empty-go" @click="adding = true">일정 등록</button>
  </div>

  <section v-for="g in groups" :key="g.day" class="day">
    <h3>{{ g.day }}</h3>
    <ul class="item-list">
      <li v-for="s in g.items" :key="s.id" class="item">
        <time class="time">{{ timeLabel(s.startAt) }}</time>
        <div class="item-text">
          <b>{{ s.title }}</b>
          <span v-if="s.memo">{{ s.memo }}</span>
          <small>{{ s.createdByName || '알 수 없음' }} 등록</small>
        </div>
        <button type="button" class="text-btn" @click="remove(s)">삭제</button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.day h3 {
  margin: 0;
  padding-top: 4px;
  font-size: 15px;
  font-weight: 800;
}
.time {
  width: 48px;
  flex: none;
  font-size: 16px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
/* 빈 카드 (웹 저장 WebSavedView 의 .empty-card 와 같은 모양, 앱 색 토큰) */
.empty-card {
  margin-top: 8px;
  padding: 28px 20px 22px;
  border: 1px solid var(--color-divider);
  border-radius: 14px;
  background: var(--color-bg-soft);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}
.empty-icon {
  width: 52px;
  height: 52px;
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 6px;
}
.empty-card b {
  font-size: 15px;
  font-weight: 700;
}
.empty-card p {
  margin: 0;
  font-size: 13.5px;
  color: var(--color-text-sub);
}
.empty-go {
  margin-top: 12px;
  padding: 9px 18px;
  border: 0;
  border-radius: 999px;
  background: var(--color-primary);
  color: var(--color-card);
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.tab-head.end {
  justify-content: flex-end; /* 문장을 빼서 + 버튼만 오른쪽에 */
}
.form-back {
  height: 44px;
  margin-left: -6px;
  padding: 0 6px;
  border: 0;
  background: transparent;
  color: var(--color-text);
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
</style>
