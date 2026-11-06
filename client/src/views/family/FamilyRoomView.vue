<script setup>
// 가족 모임방 (내 캠핑 → 가족 모임방 버튼으로 들어옴. 설계 파일 없음 — 저장 화면 모양에 맞춤)
// 방이 없으면 만들기·초대코드 입력, 있으면 방 정보 + 탭. 탭 내용은 components/family/ 에 하나씩
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useFamilyStore } from '@/stores/family'
import MeetingPlaceTab from '@/components/family/MeetingPlaceTab.vue'
import ChildCardTab from '@/components/family/ChildCardTab.vue'
import ScheduleTab from '@/components/family/ScheduleTab.vue'

const auth = useAuthStore()
const family = useFamilyStore()

// component 가 null 이면 "준비 중" (위치 공유는 3차)
const TABS = [
  { label: '모일 곳', component: MeetingPlaceTab },
  { label: '아이 카드', component: ChildCardTab },
  { label: '일정', component: ScheduleTab },
  { label: '위치', component: null },
]
const tab = ref(0)
const loading = ref(true)
const error = ref('')

// 방이 없을 때: 만들기 / 초대코드 입력
const roomName = ref('')
const inviteCode = ref('')
const startError = ref('')
const copied = ref(false)

onMounted(async () => {
  if (!auth.isLoggedIn) {
    loading.value = false
    return // 회원 전용: 로그인 안내만 보여 준다
  }
  try {
    await family.fetchRoom()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

async function create() {
  if (!roomName.value) {
    startError.value = '가족방 이름을 입력해 주세요.'
    return
  }
  await run(() => family.createRoom(roomName.value), startError)
}

async function join() {
  if (!inviteCode.value) {
    startError.value = '초대코드를 입력해 주세요.'
    return
  }
  await run(() => family.join(inviteCode.value), startError)
}

async function leave() {
  // 마지막 사람이 나가면 방과 그 안의 정보가 모두 지워지므로 한 번 더 묻는다
  const last = family.room.members.length === 1
  const msg = last
    ? '마지막 구성원이라 나가면 가족방과 모일 곳·아이 카드·일정이 모두 지워집니다. 나갈까요?'
    : '가족방에서 나갈까요?'
  if (!window.confirm(msg)) return
  if (await run(() => family.leave(), error)) tab.value = 0
}

async function copyCode() {
  try {
    await navigator.clipboard.writeText(family.room.inviteCode)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch {
    // 복사가 막힌 브라우저에서는 코드를 눈으로 보고 알려 주면 된다
  }
}

// 서버 호출 후 실패하면 해당 칸에 서버 문구를 보여 준다. 성공하면 true
async function run(fn, errorRef) {
  try {
    await fn()
    errorRef.value = ''
    return true
  } catch (e) {
    errorRef.value = e.message
    return false
  }
}
</script>

<template>
  <div class="family-room">
    <div class="head">
      <RouterLink to="/my-camping" class="back" aria-label="내 캠핑으로">‹</RouterLink>
      <h1>가족 모임방</h1>
    </div>

    <div v-if="!auth.isLoggedIn" class="empty">
      <p>가족 모임방은 로그인하면 쓸 수 있어요.</p>
      <RouterLink to="/login" class="primary">로그인</RouterLink>
    </div>

    <p v-else-if="loading" class="empty">불러오는 중...</p>

    <p v-else-if="error && !family.room" class="error" role="alert">{{ error }}</p>

    <!-- 가족방이 없을 때 -->
    <section v-else-if="!family.room" class="start">
      <p class="sub">가족이 한 방에서 모일 곳, 아이 정보, 일정, 위치를 함께 봐요.</p>

      <form class="row" @submit.prevent="create">
        <input v-model.trim="roomName" maxlength="20" placeholder="가족방 이름 (예: 우리 가족)" />
        <button type="submit" class="primary">만들기</button>
      </form>

      <div class="or">또는</div>

      <form class="row" @submit.prevent="join">
        <input v-model.trim="inviteCode" maxlength="10" placeholder="초대코드 6자리" autocapitalize="characters" />
        <button type="submit" class="secondary">들어가기</button>
      </form>

      <p v-if="startError" class="error" role="alert">{{ startError }}</p>
    </section>

    <!-- 가족방이 있을 때 -->
    <template v-else>
      <section class="room">
        <div class="room-head">
          <h2>{{ family.room.name }}</h2>
          <button type="button" class="text-btn" @click="leave">나가기</button>
        </div>
        <ul class="members">
          <li v-for="(m, i) in family.room.members" :key="i">{{ m.name }}<span v-if="m.me"> (나)</span></li>
        </ul>
        <div class="code">
          <span>초대코드 <b>{{ family.room.inviteCode }}</b></span>
          <button type="button" class="text-btn" @click="copyCode">{{ copied ? '복사됨' : '복사' }}</button>
        </div>
      </section>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <div role="tablist" class="tabs">
        <button v-for="(t, i) in TABS" :key="t.label" type="button" role="tab" :aria-selected="i === tab"
          :class="{ on: i === tab }" @click="tab = i">{{ t.label }}</button>
      </div>

      <component :is="TABS[tab].component" v-if="TABS[tab].component" />
      <p v-else class="empty">준비 중이에요.</p>
    </template>
  </div>
</template>

<style scoped>
.family-room {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px 20px;
}
.head {
  display: flex;
  align-items: center;
  gap: 4px;
}
.back {
  width: 36px;
  margin-left: -8px;
  font-size: 32px;
  line-height: 1;
  text-align: center;
  color: var(--color-text);
}
h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 900;
  letter-spacing: -0.03em;
}
h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
}
.start {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.row {
  display: flex;
  gap: 8px;
}
.or {
  text-align: center;
  font-size: 13px;
  color: var(--color-text-sub);
}
.room {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: 14px;
  background: var(--color-bg-soft);
}
.room-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.members {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.members li {
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  font-size: 13px;
}
.code {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  color: var(--color-text-mid);
}
.code b {
  margin-left: 6px;
  font-size: 18px;
  letter-spacing: 0.12em;
  color: var(--color-text);
}
.tabs {
  display: flex;
  gap: 20px;
  border-bottom: 1px solid var(--color-divider);
}
.tabs button {
  height: 44px;
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 16px;
  color: var(--color-text-sub);
  cursor: pointer;
}
.tabs button.on {
  font-weight: 700;
  color: var(--color-text);
  box-shadow: inset 0 -3px 0 var(--color-text);
}
</style>

<!-- 가족방 화면과 탭 컴포넌트(components/family/)가 같이 쓰는 입력칸·버튼 모양.
     scoped 가 아니어서 탭 안에서도 적용된다 → .family-room 안으로만 범위를 묶어 다른 화면에 새지 않게 한다 -->
<style>
.family-room .sub {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-sub);
}
.family-room .empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin: 32px 0 0;
  font-size: 15px;
  color: var(--color-text-sub);
}
.family-room .empty p {
  margin: 0;
}
.family-room input,
.family-room select,
.family-room textarea {
  flex: 1;
  min-width: 0;
  height: 48px;
  padding: 0 14px;
  border-radius: var(--radius-input);
  border: 1px solid var(--color-border);
  background: var(--color-card);
  font: inherit;
  font-size: 15px;
  color: var(--color-text);
  outline: none;
}
.family-room textarea {
  height: 72px;
  padding: 12px 14px;
  resize: vertical;
}
.family-room input:focus,
.family-room select:focus,
.family-room textarea:focus {
  border-color: var(--color-primary);
}
.family-room .primary,
.family-room .secondary {
  height: 48px;
  padding: 0 20px;
  border-radius: var(--radius-pill);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}
.family-room .primary {
  border: none;
  background: var(--color-primary);
  color: var(--color-card);
}
.family-room .secondary {
  border: 1px solid var(--color-primary);
  background: var(--color-card);
  color: var(--color-primary);
}
.family-room .secondary:disabled {
  opacity: 0.6;
  cursor: default;
}
.family-room .text-btn {
  padding: 4px 0;
  border: 0;
  background: transparent;
  font-size: 14px;
  color: var(--color-text-sub);
  text-decoration: underline;
  cursor: pointer;
}
.family-room .error {
  width: 100%;
  margin: 0;
  font-size: 13px;
  font-weight: 700;
}
/* 탭 위쪽: 안내 문구 + 추가(+) 버튼 */
.family-room .tab-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.family-room .icon {
  width: 44px;
  height: 44px;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.family-room .stack-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.family-room .stack-form input,
.family-room .stack-form select,
.family-room .stack-form textarea {
  flex: none;
}
.family-room .item-list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}
.family-room .item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-line);
}
.family-room .item-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.family-room .item-text b {
  font-size: 16px;
}
.family-room .item-text span {
  font-size: 14px;
  color: var(--color-text-mid);
}
.family-room .item-text small {
  font-size: 12px;
  color: var(--color-text-sub);
}
.family-room .item-actions {
  display: flex;
  gap: 12px;
}
</style>
