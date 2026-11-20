<script setup>
// 웹 알림: 기상특보 + 재난문자 모아 보기 (로그인 없이도 볼 수 있다)
// 데이터: GET /api/alerts  (모바일 AlertView 와 같은 API) — { warningDummy, disasterDummy, sido, regions, activeWarnings, todayDisasterCount, items }
// 위치: 브라우저 위치 → 서버가 가장 가까운 시·도로 정한다. 못 쓰면 시·도를 직접 고른다
import { computed, onMounted, ref } from 'vue'
import WebSubPage from '@/components/web/WebSubPage.vue'
import { api } from '@/utils/api'

const data = ref(null)
const error = ref('')
const loading = ref(false)
const locating = ref(false)
const filter = ref('all') // all · warning · disaster
const openId = ref(null)

const FILTERS = [
  { key: 'all', label: '전체' },
  { key: 'warning', label: '기상특보' },
  { key: 'disaster', label: '재난문자' },
]
const KIND_LABEL = { warning: '특보', disaster: '재난문자' }

const shown = computed(() => (data.value?.items ?? []).filter((a) => filter.value === 'all' || a.kind === filter.value))
const isDummy = (a) => (a.kind === 'warning' ? data.value?.warningDummy : data.value?.disasterDummy)

// 안전 정보를 지어내지 않는다: 예시인 쪽만 맨 위에 알린다
const dummyNote = computed(() => {
  const d = data.value
  if (!d) return ''
  if (d.warningDummy && d.disasterDummy) return '기상특보·재난문자 모두 예시 알림이에요. 실제 정보가 아니에요.'
  if (d.disasterDummy) return '재난문자는 예시 알림이에요. 기상특보는 기상청 실제 정보예요.'
  if (d.warningDummy) return '기상특보는 예시 알림이에요. 재난문자는 실제 정보예요.'
  return ''
})

async function load(params) {
  loading.value = true
  error.value = ''
  try {
    data.value = await api('/api/alerts', { params })
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function locate() {
  if (!navigator.geolocation) {
    load({}).then(() => (error.value = '이 브라우저는 위치를 쓸 수 없습니다. 지역을 직접 골라 주세요.'))
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      locating.value = false
      load({ lat: pos.coords.latitude, lon: pos.coords.longitude })
    },
    () => {
      locating.value = false
      load({}).then(() => (error.value = '위치를 가져오지 못했습니다. 지역을 직접 골라 주세요.'))
    },
    { timeout: 10000, maximumAge: 5 * 60 * 1000 },
  )
}
function pickRegion(e) {
  const sido = e.target.value
  load(sido ? { sido } : {})
}
onMounted(locate)

function timeText(iso) {
  const d = new Date(iso)
  if (isNaN(d)) return ''
  return d.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', month: '2-digit', day: '2-digit', hour: 'numeric', minute: '2-digit' })
}
</script>

<template>
  <WebSubPage eyebrow="안전" title="알림" desc="내 지역의 기상특보와 재난문자를 모아 봅니다" narrow>
    <p v-if="dummyNote" class="dummy" role="note"><b>예시 포함</b> {{ dummyNote }}</p>

    <section class="card region">
      <div class="rhead">
        <b>{{ locating ? '위치 확인 중…' : data?.sido ? `지금 ${data.sido}` : '전국' }}</b>
        <div class="rctl">
          <select :value="data?.sido ?? ''" :disabled="loading" aria-label="지역 직접 고르기" @change="pickRegion">
            <option value="">전국</option>
            <option v-for="r in data?.regions ?? []" :key="r" :value="r">{{ r }}</option>
          </select>
          <button type="button" class="loc" :disabled="locating || loading" @click="locate">내 위치로</button>
        </div>
      </div>
      <p v-if="error" class="err" role="alert">{{ error }}</p>
      <template v-if="data">
        <div v-if="data.activeWarnings.length" class="warnings">
          <p v-for="w in data.activeWarnings" :key="w.id" class="warning-now">
            <b>⚠ {{ w.category }} 발효 중</b>
            <span>{{ w.area }} · {{ timeText(w.issuedAt) }} {{ data.warningDummy ? '부터' : '기준' }}</span>
          </p>
        </div>
        <p v-else class="calm">발효 중인 기상특보가 없어요.</p>
        <p class="count">오늘 이 지역 재난문자 {{ data.todayDisasterCount }}건</p>
      </template>
    </section>

    <div class="tabs" role="tablist">
      <button v-for="f in FILTERS" :key="f.key" type="button" role="tab" :aria-selected="filter === f.key"
        :class="{ on: filter === f.key }" @click="filter = f.key">{{ f.label }}</button>
    </div>

    <ul class="list">
      <li v-for="a in shown" :key="a.id">
        <button type="button" class="item" :aria-expanded="openId === a.id" @click="openId = openId === a.id ? null : a.id">
          <span class="top">
            <b>{{ a.area }}</b>
            <span class="time">{{ timeText(a.issuedAt) }}</span>
          </span>
          <span class="msg" :class="{ open: openId === a.id }">
            <span :class="['chip', a.kind]">{{ KIND_LABEL[a.kind] }} · {{ a.category }}{{ isDummy(a) ? ' · 예시' : '' }}</span>
            {{ a.message }}
          </span>
        </button>
      </li>
      <li v-if="loading && !data" class="empty">불러오는 중...</li>
      <li v-else-if="data && !shown.length" class="empty">이 지역에 알림이 없어요.</li>
    </ul>

    <p class="note">공식 재난문자를 대신하지 않습니다. 휴대폰으로 오는 재난문자를 꼭 확인하세요.</p>
  </WebSubPage>
</template>

<style scoped>
.dummy {
  margin: 0 0 16px;
  padding: 12px 16px;
  border-radius: 10px;
  background: var(--web-grade-danger-bg);
  color: var(--web-grade-danger-fg);
  font-size: 14px;
}
.card {
  background: var(--web-bg);
  border: 1px solid var(--web-border-card);
  border-radius: 16px;
  padding: 22px 26px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.rhead b {
  font-size: 20px;
}
.rctl {
  display: flex;
  gap: 8px;
}
select {
  height: 40px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  font-family: inherit;
  font-size: 14px;
}
.loc {
  height: 40px;
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
.loc:disabled {
  opacity: 0.5;
}
.err {
  margin: 0;
  font-size: 13px;
  color: var(--web-text-warn);
}
.warnings {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.warning-now {
  margin: 0;
  padding: 12px 16px;
  border-radius: 10px;
  background: var(--web-warn-bg);
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 14px;
}
.warning-now b {
  color: var(--web-text-warn);
}
.calm {
  margin: 0;
  padding: 12px 16px;
  border-radius: 10px;
  background: var(--web-grade-safe-bg);
  color: var(--web-grade-safe);
  font-size: 14px;
  font-weight: 700;
}
.count {
  margin: 0;
  font-size: 14px;
  color: var(--web-text-sub);
}
.tabs {
  margin: 22px 0 12px;
  display: flex;
  gap: 8px;
}
.tabs button {
  height: 40px;
  padding: 0 20px;
  border-radius: 999px;
  border: 1px solid var(--web-line-ghost);
  background: var(--web-bg);
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}
.tabs button.on {
  background: var(--web-green);
  border-color: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
}
.list {
  margin: 0;
  padding: 0;
  list-style: none;
  background: var(--web-bg);
  border: 1px solid var(--web-border-card);
  border-radius: 16px;
  overflow: hidden;
}
.list li + li {
  border-top: 1px solid var(--web-line-card);
}
.item {
  width: 100%;
  padding: 18px 26px;
  border: 0;
  background: none;
  text-align: left;
  font-family: inherit;
  color: var(--web-text);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.top {
  display: flex;
  justify-content: space-between;
  font-size: 15px;
}
.time {
  color: var(--web-text-mute);
  font-size: 13px;
}
.msg {
  font-size: 14px;
  line-height: 1.6;
  color: var(--web-text-sub);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.msg.open {
  display: block;
}
.chip {
  margin-right: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: var(--web-bg-chip);
  color: var(--web-text);
}
.chip.warning {
  background: var(--web-weather-badge);
  color: var(--web-weather-badge-fg);
}
.chip.disaster {
  background: var(--web-warn-bg);
  color: var(--web-text-warn);
}
.empty {
  padding: 40px;
  text-align: center;
  color: var(--web-text-sub);
}
.note {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--web-text-mute);
}
</style>
