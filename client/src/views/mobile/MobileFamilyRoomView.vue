<script setup>
// 가족 모임방 (내 캠핑 → 가족 모임방 버튼으로 들어옴. 설계 파일 없음 — 저장 화면 모양에 맞춤)
// 방이 없으면 만들기·초대코드 입력, 있으면 방 정보 + 탭. 탭 내용은 components/mobile/ 에 하나씩 (MobileXxxTab)
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { askConfirm } from '@/utils/confirm'
import { goUp } from '@/utils/backStack'
import { useAuthStore } from '@/stores/auth'
import { useFamilyStore } from '@/stores/family'
import MobileMeetingPlaceTab from '@/components/mobile/MobileMeetingPlaceTab.vue'
import MobileScheduleTab from '@/components/mobile/MobileScheduleTab.vue'
import MobileLocationTab from '@/components/mobile/MobileLocationTab.vue'
import { familyColor } from '@/utils/familyColor'

const router = useRouter()

const auth = useAuthStore()
const family = useFamilyStore()

// component 가 null 이면 "준비 중"
// 위치를 맨 앞에 둔다 → 가족방에 들어오면 위치 탭이 먼저 보인다 (tab 기본값 0)
const TABS = [
  { label: '위치', component: MobileLocationTab },
  { label: '모일 곳', component: MobileMeetingPlaceTab },
  { label: '일정', component: MobileScheduleTab },
]
const tab = ref(0)

// 구성원 칩 보여 줄 순서: 로그인한 나를 맨 앞에, 나머지는 방에 들어온 순서 그대로
// 색은 "방에 들어온 순번(seq)"으로 정해야 위치 탭 지도 마커와 같아지므로, 순서를 바꾸기 전에 seq 를 붙여 둔다
const memberChips = computed(() => {
  const list = (family.room?.members ?? []).map((m, i) => ({ ...m, seq: i + 1 }))
  return [...list.filter((m) => m.me), ...list.filter((m) => !m.me)]
})
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
    startError.value = '모임방 이름을 입력해 주세요.'
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
  const message = last ? '마지막 구성원이라 나가면 모임방과 모일 곳·일정이 모두 지워집니다.' : ''
  if (!(await askConfirm({ title: '모임방에서 나갈까요?', message, confirmLabel: '나가기' }))) return
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
      <RouterLink to="/app/my-camping" class="back" aria-label="내 캠핑으로" @click.capture.prevent="goUp(router, '/app/my-camping')">‹</RouterLink>
      <h1>모임방</h1>
    </div>

    <div v-if="!auth.isLoggedIn" class="empty">
      <p>모임방은 로그인하면 쓸 수 있어요.</p>
      <RouterLink to="/app/login" class="primary">로그인</RouterLink>
    </div>

    <p v-else-if="loading" class="empty">불러오는 중...</p>

    <p v-else-if="error && !family.room" class="error" role="alert">{{ error }}</p>

    <!-- 가족방이 없을 때 -->
    <section v-else-if="!family.room" class="start">
      <p class="sub">함께 가는 사람이 한 방에서 위치, 모일 곳, 일정을 함께 봐요.</p>

      <form class="row" @submit.prevent="create">
        <input v-model.trim="roomName" maxlength="20" placeholder="모임방 이름 (예: 우리 가족)" />
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
          <!-- 사람마다 다른 색 칩. 위치 탭의 그 사람 지도 마커와 같은 색이다 (m.seq = 방에 들어온 순번 → utils/familyColor.js)
               나(로그인한 사람)는 맨 앞에 두고, "나" 글자 없이 칩을 크게 해서 강조한다 (2026-11-27 사용자 요청). 화면 읽기 프로그램에는 aria-label 로 알려 준다 -->
          <li v-for="m in memberChips" :key="m.seq" :class="{ me: m.me }" :style="{ '--chip': familyColor(m.seq) }"
            :title="m.me ? '로그인한 나' : undefined" :aria-label="m.me ? `${m.name}, 나` : undefined">{{ m.name }}</li>
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
/* 내 칩이 더 커서 높이가 다르므로, 칩끼리 세로 가운데를 맞춘다 */
.members {
  align-items: center;
}
/* --chip: 사람별 색 (템플릿에서 넣음). 지도 마커 이름표와 같은 모양 · 색 */
.members li {
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: var(--chip);
  color: var(--color-card);
  font-size: 13px;
  font-weight: 600;
}
/* 나: 글자와 칩을 키우고, 바깥에 흰 틈 + 내 색 고리를 한 겹 더 둘러서 로그인한 나를 먼저 알아보게 한다
   고리는 box-shadow 라 칩 크기에 영향이 없다 → 고리(4px)만큼 margin 을 줘서 옆 칩과 붙지 않게 한다 */
.members li.me {
  margin: 4px;
  padding: 6px 14px;
  font-size: 16px;
  font-weight: 700;
  box-shadow: 0 0 0 2px var(--color-card), 0 0 0 4px var(--chip);
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

<!-- 가족방 화면과 탭 컴포넌트(components/mobile/ 의 MobileXxxTab)가 같이 쓰는 입력칸·버튼 모양.
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
