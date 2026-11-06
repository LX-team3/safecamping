<script setup>
// 내 정보 · 여행 취향 (6개 중 최대 3개, 순서 없음). 고른 취향에 맞는 캠핑장 추천에 쓴다 (추천은 캠핑장 데이터가 들어오면 붙임)
// 선택지·최대 개수는 서버(ActivityOption)가 정본 → API 로 받아서 그린다
// 설계 TasteSurvey.dc.html 을 받지 못해, 선택 버튼 모양은 Recheck.dc.html 의 "여러 개 고르기" 버튼을 따름
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { api } from '@/utils/api'

const auth = useAuthStore()
const URL = '/api/member/activities'

const options = ref([]) // [{ code, label, desc }]
const selected = ref([]) // 고른 code 들
const max = ref(3)
const error = ref('')
const message = ref('')
const loading = ref(false)

const full = computed(() => selected.value.length >= max.value)

function fill(res) {
  options.value = res.options
  selected.value = [...res.selected]
  max.value = res.max
}

onMounted(async () => {
  if (!auth.isLoggedIn) return // 회원 전용: 로그인 안내만 보여 준다
  try {
    fill(await api(URL))
  } catch (e) {
    error.value = e.message
  }
})

function isOn(code) {
  return selected.value.includes(code)
}

// 이미 3개면 새로 고르지 못하게 막고 안내한다 (고른 것을 빼는 것은 언제나 된다)
function toggle(code) {
  message.value = ''
  if (isOn(code)) {
    selected.value = selected.value.filter((c) => c !== code)
  } else if (full.value) {
    error.value = `여행 취향은 ${max.value}개까지 고를 수 있습니다.`
    return
  } else {
    selected.value = [...selected.value, code]
  }
  error.value = ''
}

async function save() {
  error.value = ''
  message.value = ''
  loading.value = true
  try {
    fill(await api(URL, { method: 'put', body: { activities: selected.value } }))
    message.value = '저장했습니다.'
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="activity">
    <div class="head">
      <RouterLink to="/my-info" aria-label="내 정보로 돌아가기" class="back">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M15 5l-7 7 7 7" />
        </svg>
      </RouterLink>
      <h1>여행 취향</h1>
    </div>

    <div v-if="!auth.isLoggedIn" class="empty">
      <p>여행 취향은 로그인하면 고를 수 있습니다.</p>
      <RouterLink to="/login" class="primary">로그인</RouterLink>
    </div>

    <template v-else>
      <div class="body">
        <div class="title">
          <b>캠핑 가서 무엇을 하고 싶나요?</b>
          <span>{{ max }}개까지 고를 수 있습니다 · {{ selected.length }} / {{ max }}</span>
        </div>

        <div class="options">
          <button v-for="o in options" :key="o.code" type="button" class="option" :class="{ on: isOn(o.code) }"
            :aria-pressed="isOn(o.code)" @click="toggle(o.code)">
            <span class="text"><b>{{ o.label }}</b><span>{{ o.desc }}</span></span>
            <span class="mark" aria-hidden="true">{{ isOn(o.code) ? '✓' : '' }}</span>
          </button>
        </div>

        <span class="note">고른 취향에 맞는 캠핑장을 먼저 추천해 드립니다.</span>
      </div>

      <div class="foot">
        <p v-if="error" class="warn" role="alert">{{ error }}</p>
        <p v-else-if="message" class="ok" role="status">{{ message }}</p>
        <button type="button" class="save" :disabled="loading" @click="save">저장하기</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.activity {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.head {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 16px;
}
.back {
  width: 44px;
  height: 44px;
  margin-left: -10px;
  display: flex;
  align-items: center;
  justify-content: center;
}
h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 900;
  letter-spacing: -0.02em;
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 20px 24px;
}
.title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.title b {
  font-size: 20px;
  font-weight: 900;
  letter-spacing: -0.02em;
}
.title span {
  font-size: 13px;
  color: var(--color-text-sub);
}
.options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
/* 설계 Recheck 의 선택 버튼: 안 고름 = 얇은 테두리, 고름 = 굵은 검정 테두리 + 연한 배경 + 체크 원 */
.option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 60px;
  padding: 10px 16px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
  font-family: inherit;
  cursor: pointer;
}
.option.on {
  border: 2px solid var(--color-text);
  background: var(--color-bg-soft);
}
.text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
}
.text b {
  font-size: 16px;
}
.text span {
  font-size: 12px;
  color: var(--color-text-sub);
}
.mark {
  flex: none;
  width: 26px;
  height: 26px;
  border-radius: 13px;
  border: 1.5px solid var(--color-text-light);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 900;
}
.option.on .mark {
  border: 0;
  background: var(--color-text);
  color: var(--color-card);
}
.note {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  font-size: 13px;
  color: var(--color-text-mid);
}
.foot {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 20px 24px;
  border-top: 1px solid var(--color-divider);
}
.save {
  height: 54px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-text);
  color: var(--color-card);
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.save:disabled {
  opacity: 0.6;
}
.ok,
.warn {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
}
.ok {
  color: var(--color-green);
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin: 48px 20px 0;
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
</style>
