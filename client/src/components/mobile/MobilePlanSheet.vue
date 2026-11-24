<script setup>
// "내 캠핑에 일정 등록" 시트 — 캠핑장 상세와 차박 판정 카드(등록 야영장)가 같이 쓴다
// 등록하면 내 캠핑 탭에 카드로 뜬다 (서버 저장 POST /api/member/camp-plans, stores/myCamping.js). 회원 전용
// 단계: login(로그인 필요) → form(날짜 입력) → done(저장 끝, "내 캠핑에서 보기")
// 등록 대상 두 가지: 등록 캠핑장(campId) 또는 캠핑장이 아닌 자리(차박 후보지 · 찍은 자리 = lat·lng, campId 없음)
// 둘 중 하나는 있어야 한다 (서버·DB 가 한 번 더 검사)
import { ref } from 'vue'
import MobileBottomSheet from '@/components/mobile/MobileBottomSheet.vue'
import { useAuthStore } from '@/stores/auth'
import { useMyCampingStore } from '@/stores/myCamping'
import { useTripStore } from '@/stores/trip'
import { todayIso, nightsBetween } from '@/utils/date'

const props = defineProps({
  campId: { type: Number, default: null }, // 캠핑장이 아닌 자리면 null
  campName: { type: String, required: true }, // 캠핑장 이름 또는 자리 이름 ("475-3 잡종지")
  place: { type: String, default: '' }, // 내 캠핑 카드에 보일 지역 ("강원 인제군 북면")
  lat: { type: Number, default: null }, // 자리일 때의 위치
  lng: { type: Number, default: null },
})
const emit = defineEmits(['close'])

const auth = useAuthStore()
const myCamping = useMyCampingStore()
const trip = useTripStore()

// 처음 열 때: 로그인 안 했으면 로그인 안내, 했으면 탐색에서 고른 날짜를 미리 채운 입력칸
const step = ref(auth.isLoggedIn ? 'form' : 'login')
const checkIn = ref(trip.state.checkIn || '') // "2026-11-14"
const checkOut = ref(trip.state.checkOut || '')
const memo = ref('')
const error = ref('')
const saving = ref(false)

// 서버와 같은 규칙을 먼저 화면에서 확인한다 (서버도 한 번 더 검사)
async function save() {
  if (!checkIn.value || !checkOut.value) {
    error.value = '체크인·체크아웃 날짜를 골라 주세요.'
    return
  }
  if (checkIn.value < todayIso()) {
    error.value = '지난 날짜는 고를 수 없습니다.'
    return
  }
  if (nightsBetween(checkIn.value, checkOut.value) < 1) {
    error.value = '체크아웃은 체크인 다음 날부터 고를 수 있습니다.'
    return
  }
  error.value = ''
  saving.value = true
  try {
    await myCamping.add({
      campId: props.campId,
      lat: props.campId == null ? props.lat : null, // 캠핑장 등록이면 좌표는 안 보낸다
      lng: props.campId == null ? props.lng : null,
      campName: props.campName,
      place: props.place,
      checkIn: checkIn.value,
      checkOut: checkOut.value,
      memo: memo.value.trim(),
    })
    step.value = 'done'
  } catch (e) {
    error.value = e.message // 중복 등록 등 서버 문구를 그대로 보여 준다
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <MobileBottomSheet title="내 캠핑에 일정 등록" @close="emit('close')">
    <div v-if="step === 'login'" class="plan-msg">
      <p>일정은 로그인하면 저장할 수 있어요.</p>
      <RouterLink to="/app/login" class="plan-go">로그인하기</RouterLink>
    </div>

    <div v-else-if="step === 'done'" class="plan-msg">
      <p><b>내 캠핑에 등록했어요.</b><br />내 캠핑 탭에서 확인할 수 있어요.</p>
      <RouterLink to="/app/my-camping" class="plan-go">내 캠핑에서 보기</RouterLink>
    </div>

    <form v-else class="plan-form" @submit.prevent="save">
      <p class="plan-camp">{{ campName }}</p>
      <label>체크인
        <input v-model="checkIn" type="date" :min="todayIso()" />
      </label>
      <label>체크아웃
        <input v-model="checkOut" type="date" :min="checkIn || todayIso()" />
      </label>
      <label>메모 (선택)
        <input v-model.trim="memo" maxlength="100" placeholder="예: 예약번호, 준비물" />
      </label>
      <p v-if="error" class="plan-error" role="alert">{{ error }}</p>
      <button type="submit" class="plan-go" :disabled="saving">{{ saving ? '등록 중...' : '등록하기' }}</button>
    </form>
  </MobileBottomSheet>
</template>

<style scoped>
.plan-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.plan-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-sub);
}
.plan-form input {
  height: 48px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font: inherit;
  font-size: 15px;
  color: var(--color-text);
}
.plan-camp {
  margin: 0;
  font-size: 17px;
  font-weight: 900;
}
.plan-error {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-danger);
}
.plan-go:disabled {
  opacity: 0.5;
  cursor: default;
}
.plan-msg {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 0 4px;
  text-align: center;
}
.plan-msg p {
  margin: 0;
  font-size: 15px;
  line-height: 1.5;
}
.plan-go {
  height: 50px;
  border: none;
  border-radius: 25px;
  background: var(--color-text);
  color: var(--color-card);
  display: flex;
  align-items: center;
  justify-content: center;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
</style>
