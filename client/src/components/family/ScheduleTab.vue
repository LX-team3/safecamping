<script setup>
// 가족방 · 일정 탭. 가족이 함께 보는 일정 (예: 06:00 출발, 15:00 체크인). 시간 순, 날짜별로 묶어 보여 준다
import { ref, computed, onMounted } from 'vue'
import { useFamilyStore } from '@/stores/family'
import AddButton from './AddButton.vue'

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
  if (!window.confirm(`'${s.title}' 일정을 지울까요?`)) return
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
  <div class="tab-head">
    <p class="sub">가족이 함께 볼 일정을 적어 두세요.</p>
    <AddButton label="일정 추가" @click="adding = !adding" />
  </div>

  <form v-if="adding" class="stack-form" @submit.prevent="add">
    <input v-model.trim="title" maxlength="50" placeholder="일정 (예: 집에서 출발)" />
    <input v-model="startAt" type="datetime-local" aria-label="날짜와 시간" />
    <input v-model.trim="memo" maxlength="200" placeholder="메모 (선택)" />
    <button type="submit" class="primary">추가</button>
    <p v-if="formError" class="error" role="alert">{{ formError }}</p>
  </form>

  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p v-if="!family.schedules.length && !adding" class="empty">아직 등록한 일정이 없어요.</p>

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
</style>
