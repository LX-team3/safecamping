<script setup>
// 웹 모임방(가족 모임방): 방 만들기 · 초대코드로 들어가기 → 모일 곳 · 일정 (모바일 FamilyRoomView 와 같은 API · 같은 store)
// 가족 위치 탭은 지도가 필요해 웹에는 아직 없다 (모바일에서 쓴다)
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import WebSubPage from '@/components/web/WebSubPage.vue'
import WebConfirmModal from '@/components/web/WebConfirmModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useFamilyStore } from '@/stores/family'

const auth = useAuthStore()
const family = useFamilyStore()

const TABS = ['모일 곳', '일정']
const tab = ref(0)
const loading = ref(true)
const error = ref('')
const startError = ref('')
const roomName = ref('')
const inviteCode = ref('')
const copied = ref(false)

// 삭제 확인 팝업 (브라우저 기본 confirm 대신)
const confirmState = ref(null) // { title, text, label, run }
const confirmBusy = ref(false)
function ask(title, text, label, run) {
  confirmState.value = { title, text, label, run }
}
async function doConfirm() {
  confirmBusy.value = true
  try {
    await confirmState.value.run()
  } catch (e) {
    error.value = e.message
  } finally {
    confirmBusy.value = false
    confirmState.value = null
  }
}

async function run(fn, target) {
  try {
    await fn()
    target.value = ''
    return true
  } catch (e) {
    target.value = e.message
    return false
  }
}

onMounted(async () => {
  if (!auth.isLoggedIn) {
    loading.value = false
    return
  }
  try {
    await family.fetchRoom()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

const create = () => (roomName.value ? run(() => family.createRoom(roomName.value), startError) : (startError.value = '가족방 이름을 입력해 주세요.'))
const join = () => (inviteCode.value ? run(() => family.join(inviteCode.value), startError) : (startError.value = '초대코드를 입력해 주세요.'))

function leave() {
  const last = family.room.members.length === 1
  ask(
    '가족방에서 나갈까요?',
    last ? '마지막 구성원이라 나가면 가족방과 모일 곳·일정이 모두 지워집니다.' : '나가도 다른 가족은 그대로 방을 쓸 수 있어요.',
    '나가기',
    async () => {
      await family.leave()
      tab.value = 0
    },
  )
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

// 탭을 열 때 그 목록을 불러온다
const FETCH = [() => family.fetchPlaces(), () => family.fetchSchedules()]
watch(
  [() => family.room?.id, tab],
  async () => {
    if (!family.room) return
    try {
      await FETCH[tab.value]()
    } catch (e) {
      error.value = e.message
    }
  },
  { immediate: true },
)

// ---- 모일 곳 ----
const addingPlace = ref(false)
const place = reactive({ name: '', description: '', coords: null })
const placeError = ref('')
const locating = ref(false)
function useMyLocation() {
  if (!navigator.geolocation) return (placeError.value = '이 브라우저에서는 위치를 가져올 수 없습니다.')
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      place.coords = { lon: pos.coords.longitude, lat: pos.coords.latitude }
      locating.value = false
      placeError.value = ''
    },
    () => {
      locating.value = false
      placeError.value = '위치를 가져오지 못했습니다. 위치 권한을 확인해 주세요.'
    },
    { enableHighAccuracy: true, timeout: 10000 },
  )
}
async function addPlace() {
  if (!place.name) return (placeError.value = '장소 이름을 입력해 주세요.')
  const ok = await run(() => family.addPlace({ name: place.name, description: place.description, ...place.coords }), placeError)
  if (ok) {
    Object.assign(place, { name: '', description: '', coords: null })
    addingPlace.value = false
  }
}
const mapLink = (p) => `https://map.kakao.com/link/map/${encodeURIComponent(p.name)},${p.lat},${p.lon}`

// ---- 일정 ----
const addingSch = ref(false)
const sch = reactive({ title: '', startAt: '', memo: '' })
const schError = ref('')
async function addSchedule() {
  if (!sch.title) return (schError.value = '일정 내용을 입력해 주세요.')
  if (!sch.startAt) return (schError.value = '날짜와 시간을 골라 주세요.')
  const ok = await run(() => family.addSchedule({ title: sch.title, startAt: new Date(sch.startAt).toISOString(), memo: sch.memo }), schError)
  if (ok) {
    Object.assign(sch, { title: '', startAt: '', memo: '' })
    addingSch.value = false
  }
}
const KST = { timeZone: 'Asia/Seoul' }
const dayLabel = (iso) => new Date(iso).toLocaleDateString('ko-KR', { ...KST, month: 'numeric', day: 'numeric', weekday: 'short' })
const timeLabel = (iso) => new Date(iso).toLocaleTimeString('ko-KR', { ...KST, hour: '2-digit', minute: '2-digit', hour12: false })
const schGroups = computed(() => {
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
  <WebSubPage eyebrow="함께 가는 사람" title="모임방" desc="가족이 한 방에서 모일 곳과 일정을 함께 봐요" narrow>
    <div v-if="!auth.isLoggedIn" class="card center">
      <p>모임방은 로그인하면 쓸 수 있어요.</p>
      <RouterLink to="/web/login" class="primary">로그인</RouterLink>
    </div>
    <p v-else-if="loading" class="state">불러오는 중...</p>
    <p v-else-if="error && !family.room" class="state" role="alert">{{ error }}</p>

    <!-- 방이 없을 때 -->
    <section v-else-if="!family.room" class="card start">
      <h2>새 가족방 만들기</h2>
      <form class="row" @submit.prevent="create">
        <input v-model.trim="roomName" maxlength="20" placeholder="가족방 이름 (예: 우리 가족)" />
        <button type="submit" class="primary">만들기</button>
      </form>
      <div class="or">또는</div>
      <h2>초대코드로 들어가기</h2>
      <form class="row" @submit.prevent="join">
        <input v-model.trim="inviteCode" maxlength="10" placeholder="초대코드 6자리" autocapitalize="characters" />
        <button type="submit" class="ghost">들어가기</button>
      </form>
      <p v-if="startError" class="err" role="alert">{{ startError }}</p>
    </section>

    <!-- 방이 있을 때 -->
    <template v-else>
      <section class="card room">
        <div class="rtop">
          <h2>{{ family.room.name }}</h2>
          <button type="button" class="text-btn" @click="leave">나가기</button>
        </div>
        <ul class="members">
          <li v-for="(m, i) in family.room.members" :key="i" :class="{ me: m.me }">{{ m.name }}{{ m.me ? ' (나)' : '' }}</li>
        </ul>
        <div class="code">
          <span>초대코드 <b>{{ family.room.inviteCode }}</b></span>
          <button type="button" class="text-btn" @click="copyCode">{{ copied ? '복사됨' : '복사' }}</button>
        </div>
      </section>

      <p v-if="error" class="err" role="alert">{{ error }}</p>

      <div class="tabs" role="tablist">
        <button v-for="(t, i) in TABS" :key="t" type="button" role="tab" :aria-selected="i === tab" :class="{ on: i === tab }" @click="tab = i">{{ t }}</button>
      </div>

      <!-- 모일 곳 -->
      <section v-if="tab === 0" class="card">
        <div class="shead"><h2>모일 곳</h2><button type="button" class="add" @click="addingPlace = !addingPlace">{{ addingPlace ? '닫기' : '+ 추가' }}</button></div>
        <form v-if="addingPlace" class="form" @submit.prevent="addPlace">
          <input v-model.trim="place.name" maxlength="30" placeholder="장소 이름 (예: 주차장 앞)" />
          <input v-model.trim="place.description" maxlength="100" placeholder="설명 (선택)" />
          <div class="frow">
            <button type="button" class="ghost" :disabled="locating" @click="useMyLocation">{{ place.coords ? '내 위치 저장됨 ✓' : locating ? '확인 중…' : '내 위치로 지정' }}</button>
            <button type="submit" class="primary">저장</button>
          </div>
          <p v-if="placeError" class="err" role="alert">{{ placeError }}</p>
        </form>
        <p v-if="!family.places.length" class="empty">아직 정한 모일 곳이 없어요.</p>
        <ul class="list">
          <li v-for="p in family.places" :key="p.id">
            <div class="ltx"><b>{{ p.name }}</b><span v-if="p.description">{{ p.description }}</span><small>{{ p.createdByName }}님이 추가</small></div>
            <div class="lbtn">
              <a v-if="p.lat != null" :href="mapLink(p)" target="_blank" rel="noopener" class="mini">지도</a>
              <button type="button" class="mini del" @click="ask(`'${p.name}'을(를) 지울까요?`, '모일 곳 목록에서 지워져요.', '삭제', () => family.deletePlace(p.id))">삭제</button>
            </div>
          </li>
        </ul>
      </section>

      <!-- 일정 -->
      <section v-if="tab === 1" class="card">
        <div class="shead"><h2>가족 일정</h2><button type="button" class="add" @click="addingSch = !addingSch">{{ addingSch ? '닫기' : '+ 추가' }}</button></div>
        <form v-if="addingSch" class="form" @submit.prevent="addSchedule">
          <input v-model.trim="sch.title" maxlength="50" placeholder="일정 내용 (예: 장보기)" />
          <input v-model="sch.startAt" type="datetime-local" aria-label="날짜와 시간" />
          <input v-model.trim="sch.memo" maxlength="100" placeholder="메모 (선택)" />
          <div class="frow"><span></span><button type="submit" class="primary">저장</button></div>
          <p v-if="schError" class="err" role="alert">{{ schError }}</p>
        </form>
        <p v-if="!schGroups.length" class="empty">등록된 일정이 없어요.</p>
        <div v-for="g in schGroups" :key="g.day" class="day">
          <h3>{{ g.day }}</h3>
          <ul class="list">
            <li v-for="s in g.items" :key="s.id">
              <div class="ltx"><b>{{ timeLabel(s.startAt) }} · {{ s.title }}</b><span v-if="s.memo">{{ s.memo }}</span><small>{{ s.createdByName }}님이 추가</small></div>
              <div class="lbtn"><button type="button" class="mini del" @click="ask(`'${s.title}' 일정을 지울까요?`, '', '삭제', () => family.deleteSchedule(s.id))">삭제</button></div>
            </li>
          </ul>
        </div>
      </section>
    </template>

    <WebConfirmModal :open="!!confirmState" :title="confirmState?.title" :text="confirmState?.text" :confirm-label="confirmState?.label"
      :busy="confirmBusy" @confirm="doConfirm" @cancel="confirmState = null" />
  </WebSubPage>
</template>

<style scoped>
.card {
  background: var(--web-bg);
  border: 1px solid var(--web-border-card);
  border-radius: 16px;
  padding: 26px 28px;
  margin-bottom: 16px;
}
.card.center {
  text-align: center;
  padding: 60px 26px;
}
.state {
  text-align: center;
  color: var(--web-text-sub);
  padding: 80px 0;
}
h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 800;
}
.primary,
.ghost {
  height: 46px;
  padding: 0 26px;
  border-radius: 999px;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.primary {
  border: 0;
  background: var(--web-green);
  color: var(--web-white);
}
.ghost {
  border: 1px solid var(--web-green);
  background: var(--web-bg);
  color: var(--web-green);
}
.ghost:disabled {
  opacity: 0.5;
}
.start {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.row {
  display: flex;
  gap: 10px;
}
input {
  height: 46px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  font-family: inherit;
  font-size: 15px;
  box-sizing: border-box;
  min-width: 0;
}
.row input {
  flex: 1;
}
.or {
  text-align: center;
  color: var(--web-text-mute);
  font-size: 13px;
}
.err {
  margin: 0;
  font-size: 13px;
  color: var(--web-text-warn);
}
.rtop,
.shead,
.code {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.members {
  margin: 14px 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.members li {
  padding: 6px 14px;
  border-radius: 999px;
  background: var(--web-bg-chip);
  font-size: 14px;
}
.members li.me {
  background: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
.code {
  padding: 12px 16px;
  border-radius: 10px;
  background: var(--web-bg-check);
  font-size: 14px;
}
.code b {
  margin-left: 6px;
  font-size: 18px;
  letter-spacing: 2px;
}
.text-btn {
  border: 0;
  background: none;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  color: var(--web-green);
  cursor: pointer;
}
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.tabs button {
  height: 42px;
  padding: 0 22px;
  border-radius: 999px;
  border: 1px solid var(--web-line-ghost);
  background: var(--web-bg);
  font-family: inherit;
  font-size: 15px;
  cursor: pointer;
}
.tabs button.on {
  background: var(--web-green);
  border-color: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
.add {
  height: 36px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid var(--web-green);
  background: var(--web-bg);
  color: var(--web-green);
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.form {
  margin: 16px 0;
  padding: 18px;
  border-radius: 12px;
  background: var(--web-bg-check);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.frow {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}
.empty {
  margin: 24px 0 8px;
  text-align: center;
  color: var(--web-text-sub);
  font-size: 14px;
}
.list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}
.list li {
  padding: 16px 0;
  border-top: 1px solid var(--web-line-card);
  display: flex;
  justify-content: space-between;
  gap: 16px;
}
.ltx {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.ltx b {
  font-size: 16px;
}
.ltx span {
  font-size: 14px;
  color: var(--web-text-prep);
}
.ltx small {
  font-size: 12px;
  color: var(--web-text-mute);
}
.lbtn {
  flex: none;
  display: flex;
  gap: 6px;
  align-items: flex-start;
}
.mini {
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--web-line-ghost);
  background: var(--web-bg);
  font-family: inherit;
  font-size: 13px;
  color: var(--web-text);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
}
.mini.del {
  color: var(--web-terracotta);
}
.day h3 {
  margin: 20px 0 0;
  font-size: 15px;
  color: var(--web-green);
}
</style>
