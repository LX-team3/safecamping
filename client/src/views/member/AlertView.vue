<script setup>
// 내 정보 · 알림 — 기상특보 + 재난문자 모아 보기. 설계 파일 없음 (예시로 받은 "재난 안전문자 대시보드" 목록 모양을 참고)
// 지역: 지금 내 위치(브라우저) → 서버가 가장 가까운 시·도로 정한다. 위치를 못 쓰면 시·도를 직접 고른다
// 기상특보는 기상청, 재난문자는 행정안전부 긴급재난문자 실제 데이터 (서버가 못 받으면 각각 예시). 로그인 없이도 볼 수 있다
import { ref, computed, onMounted } from 'vue'
import { api } from '@/utils/api'

// AlertResponse: { warningDummy, disasterDummy, sido, regions, activeWarnings, todayDisasterCount, items }
const data = ref(null)
const error = ref('')
const loading = ref(false)
const locating = ref(false)
const filter = ref('all') // all · warning · disaster
const openId = ref(null) // 내용을 펼친 알림

const FILTERS = [
  { key: 'all', label: '전체' },
  { key: 'warning', label: '기상특보' },
  { key: 'disaster', label: '재난문자' },
]
const KIND_LABEL = { warning: '특보', disaster: '재난문자' }

const shown = computed(() =>
  (data.value?.items ?? []).filter((a) => filter.value === 'all' || a.kind === filter.value),
)

// 이 알림이 예시(더미)인지. 기상특보·재난문자가 따로 정해진다
function isDummy(a) {
  return a.kind === 'warning' ? data.value?.warningDummy : data.value?.disasterDummy
}

// 맨 위 안내 문구: 실제로 가짜인 쪽만 알려 준다
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

// 브라우저 위치 → 서버. 권한 거부·시간 초과·지원 안 함이면 전국을 보여 주고 지역을 직접 고르게 한다
function locate() {
  if (!navigator.geolocation) {
    error.value = '이 브라우저는 위치를 쓸 수 없습니다. 지역을 직접 골라 주세요.'
    load({})
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
      load({}).then(() => {
        error.value = '위치를 가져오지 못했습니다. 지역을 직접 골라 주세요.'
      })
    },
    { timeout: 10000, maximumAge: 5 * 60 * 1000 }, // 5분 안에 받은 위치는 다시 쓴다
  )
}

function pickRegion(e) {
  const sido = e.target.value
  load(sido ? { sido } : {})
}

onMounted(locate)

// "11.06 오후 4:17" (한국 시간)
function timeText(iso) {
  const d = new Date(iso)
  if (isNaN(d)) return ''
  return d.toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul', month: '2-digit', day: '2-digit', hour: 'numeric', minute: '2-digit',
  })
}
</script>

<template>
  <div class="alert-page">
    <div class="head">
      <RouterLink to="/my-info" aria-label="내 정보로 돌아가기" class="back">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M15 5l-7 7 7 7" />
        </svg>
      </RouterLink>
      <h1>알림</h1>
    </div>

    <div class="body">
      <!-- 안전 정보를 지어내지 않는다는 규칙: 더미면 맨 위에 항상 보이게 -->
      <p v-if="dummyNote" class="dummy" role="note">
        <b>예시 포함</b> {{ dummyNote }}
      </p>

      <!-- 지금 지역 상태 -->
      <section class="region">
        <div class="region-head">
          <b>{{ locating ? '위치 확인 중…' : data?.sido ? `지금 ${data.sido}` : '전국' }}</b>
          <button type="button" class="text-btn" :disabled="locating || loading" @click="locate">내 위치로</button>
        </div>
        <label class="pick">
          <span>지역 직접 고르기</span>
          <select :value="data?.sido ?? ''" :disabled="loading" @change="pickRegion">
            <option value="">전국</option>
            <option v-for="r in data?.regions ?? []" :key="r" :value="r">{{ r }}</option>
          </select>
        </label>

        <template v-if="data">
          <div v-if="data.activeWarnings.length" class="warnings">
            <p v-for="w in data.activeWarnings" :key="w.id" class="warning-now">
              <b>⚠ {{ w.category }} 발효 중</b>
              <!-- 기상청 특보현황은 특보마다 시작 시각을 주지 않아 "현황 기준 시각"으로 보여 준다 (시작 시각을 지어내지 않기 위해) -->
              <span>{{ w.area }} · {{ timeText(w.issuedAt) }} {{ data.warningDummy ? '부터' : '기준' }}</span>
            </p>
          </div>
          <p v-else class="calm">발효 중인 기상특보가 없어요.</p>
          <p class="count">오늘 이 지역 재난문자 {{ data.todayDisasterCount }}건</p>
        </template>
      </section>

      <p v-if="error" class="warn" role="alert">{{ error }}</p>

      <div class="filters" role="tablist">
        <button v-for="f in FILTERS" :key="f.key" type="button" role="tab" :aria-selected="filter === f.key"
          :class="{ on: filter === f.key }" @click="filter = f.key">{{ f.label }}</button>
      </div>

      <ul class="list">
        <li v-for="a in shown" :key="a.id">
          <button type="button" class="item" :aria-expanded="openId === a.id"
            @click="openId = openId === a.id ? null : a.id">
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
        <li v-if="data && !shown.length" class="empty">이 지역에 알림이 없어요.</li>
      </ul>

      <p class="note">
        공식 재난문자를 대신하지 않습니다. 휴대폰으로 오는 재난문자를 꼭 확인하세요.
      </p>
    </div>
  </div>
</template>

<style scoped>
.alert-page {
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
  gap: 14px;
  padding: 4px 20px 24px;
}
.dummy {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-input);
  border: 1px solid var(--color-border);
  background: var(--color-bg-soft);
  font-size: 13px;
  color: var(--color-text-sub);
}
.dummy b {
  margin-right: 4px;
  color: var(--color-text);
}
.region {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--color-divider);
}
.region-head {
  display: flex;
  align-items: center;
}
.region-head b {
  flex: 1;
  font-size: 18px;
  font-weight: 900;
}
.text-btn {
  padding: 0;
  border: 0;
  background: transparent;
  font-family: inherit;
  font-size: 13px;
  color: var(--color-text-mid);
  text-decoration: underline;
  cursor: pointer;
}
.pick {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--color-text-sub);
}
.pick select {
  flex: 1;
  height: 40px;
  padding: 0 10px;
  border-radius: var(--radius-input);
  border: 1px solid var(--color-border);
  background: var(--color-card);
  font-family: inherit;
  font-size: 14px;
  color: var(--color-text);
}
.warnings {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
/* 발효 중인 특보: 1분 진단의 위험 안내와 같은 색 (설계 Survey.dc.html) */
.warning-now {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: 13px;
}
.warning-now b {
  font-size: 15px;
}
.calm {
  margin: 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  font-size: 14px;
  color: var(--color-text-mid);
}
.count {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-sub);
}
.warn {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
}
.filters {
  display: flex;
  gap: 8px;
}
.filters button {
  height: 36px;
  padding: 0 14px;
  border-radius: 18px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  font-family: inherit;
  font-size: 14px;
  color: var(--color-text);
  cursor: pointer;
}
.filters button.on {
  border-color: var(--color-text);
  background: var(--color-text);
  color: var(--color-card);
  font-weight: 700;
}
.list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
}
.item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 0;
  border: 0;
  border-bottom: 1px solid var(--color-line);
  background: transparent;
  font-family: inherit;
  text-align: left;
  color: var(--color-text);
  cursor: pointer;
}
.top {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.top b {
  flex: 1;
  font-size: 15px;
}
.time {
  font-size: 12px;
  color: var(--color-text-sub);
}
/* 내용은 두 줄까지만, 누르면 전부 */
.msg {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.5;
  color: var(--color-text-mid);
}
.msg.open {
  display: block;
}
.chip {
  display: inline-block;
  margin-right: 6px;
  padding: 0 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  line-height: 22px;
}
.chip.warning {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}
.chip.disaster {
  background: var(--color-fill-soft);
  color: var(--color-text-mid);
}
.empty {
  padding: 24px 0;
  text-align: center;
  font-size: 14px;
  color: var(--color-text-sub);
}
.note {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  font-size: 13px;
  color: var(--color-text-mid);
  line-height: 1.5;
}
</style>
