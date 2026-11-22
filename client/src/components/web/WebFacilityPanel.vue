<script setup>
// 시설 상세 (설계 4-1-2): 왼쪽 패널 안. 응급실·소방서 등 한 곳의 종류 · 이름 · 캠핑장에서 걸리는 시간 · 운영 시간 · 전화 · 안내
// 데이터: GET /api/facilities/{id}?campId=, 경로 GET /api/camps/{campId}/routes (이 시설의 도로 경로를 지도에 그린다)
// 전화·주소는 지어내지 않는다 → 서버에 없으면 "확인 필요"
import { computed, ref, watch } from 'vue'
import { api } from '@/utils/api'
import { FACILITY_LABEL, directionsUrl, shortSido } from '@/utils/place'

const props = defineProps({
  facId: { type: Number, required: true },
  campId: { type: Number, default: null }, // 어느 캠핑장에서 들어왔는지 (없으면 시간·경로 없이 시설 정보만)
})
// back: 캠핑장 상세로 · close: 목록으로 · routes: 지도에 그릴 경로
const emit = defineEmits(['back', 'close', 'routes'])

const CHIP_COLOR = { er: 'hospital', fire: 'fire', police: 'police', store: 'store', pharmacy: 'pharmacy', shelter: 'shelter' }
const NOTE_LABEL = { danger: '위험할 땐', caution: '주의', safe: '안전' }

const fac = ref(null)
const error = ref('')
const copied = ref('')

const cssVar = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim()
const colorOf = (type) => cssVar(`--web-fac-${CHIP_COLOR[type] || 'shelter'}`)

async function load() {
  error.value = ''
  fac.value = null
  emit('routes', [])
  try {
    fac.value = await api(`/api/facilities/${props.facId}`, { params: { campId: props.campId ?? undefined } })
  } catch (e) {
    error.value = e.message
    return
  }
  // 경로는 따로 받는다: 카카오가 느리거나 실패해도 시설 정보는 먼저 보여야 해서
  const f = fac.value
  const draw = (path) => emit('routes', [{ key: String(f.id), type: f.type, name: f.name, lat: f.lat, lng: f.lng, color: colorOf(f.type), label: f.from ? `${f.from.driveMin}분` : '', path }])
  if (!props.campId || !f.from) return draw(undefined)
  try {
    const res = await api(`/api/camps/${props.campId}/routes`)
    draw(res.routes.find((r) => r.facilityId === f.id && r.ok)?.path)
  } catch {
    draw(undefined) // 못 받으면 직선
  }
}
watch(() => [props.facId, props.campId], load, { immediate: true })

const label = computed(() => FACILITY_LABEL[fac.value?.type] || '')
const place = computed(() => {
  const f = fac.value
  return f ? [shortSido(f.sido), f.sigungu, f.eupmyeon].filter(Boolean).join(' ') : ''
})
const tone = computed(() => (fac.value?.from?.tone === 'safe' ? 'safe' : 'caution'))
const origin = computed(() => (fac.value?.from ? { name: fac.value.from.campName, lat: fac.value.from.lat, lng: fac.value.from.lng } : null))
const directions = computed(() => (fac.value ? directionsUrl(fac.value, origin.value) : '#'))

async function copyPhone() {
  try {
    await navigator.clipboard.writeText(fac.value.phone)
    copied.value = '복사했어요'
  } catch {
    copied.value = '복사하지 못했어요'
  }
}
</script>

<template>
  <div class="facpanel" role="dialog" :aria-label="`${fac?.name || '시설'} 상세`">
    <div class="bar">
      <button v-if="campId && fac?.from" type="button" class="back" @click="emit('back')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg>{{ fac.from.campName }}
      </button>
      <b v-else class="plain">시설 상세</b>
      <span class="sp"></span>
      <button type="button" class="x" aria-label="시설 상세 닫기" @click="emit('close')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
      </button>
    </div>

    <p v-if="error" class="state">{{ error }} <button type="button" class="link" @click="load">다시 시도</button></p>
    <p v-else-if="!fac" class="state">불러오는 중...</p>

    <template v-else>
      <div class="body">
        <div class="head">
          <span class="tag">{{ label }}</span>
          <h2>{{ fac.name }}</h2>
          <span class="addr">{{ fac.address || `${place} · 상세 주소 확인 필요` }}</span>
        </div>

        <div class="cards">
          <div v-if="fac.from" class="card" :class="tone">
            <small>{{ tone === 'safe' ? '안전' : '주의' }} · {{ fac.from.campName }}에서</small>
            <b>약 {{ fac.from.driveMin }}분</b>
            <span>{{ fac.from.distanceKm }}km</span>
          </div>
          <div class="card plain">
            <small>운영 시간</small>
            <b :class="{ missing: !fac.hours }">{{ fac.hours || '확인 필요' }}</b>
          </div>
        </div>

        <dl class="rows">
          <div class="row"><dt>종류</dt><dd>{{ label }}</dd><span></span></div>
          <div class="row">
            <dt>전화</dt>
            <dd :class="{ missing: !fac.phone }">{{ fac.phone || '확인 필요' }}</dd>
            <button v-if="fac.phone" type="button" @click="copyPhone">{{ copied || '복사' }}</button>
            <span v-else></span>
          </div>
        </dl>

        <ul v-if="fac.notes?.length" class="notes">
          <li v-for="(n, i) in fac.notes" :key="i" :class="n.level"><b>{{ NOTE_LABEL[n.level] }}</b><p>{{ n.body }}</p></li>
        </ul>
        <p v-if="fac.dummy" class="dummy">거리·시간·위치는 예시 데이터예요.</p>
      </div>

      <div class="foot">
        <a :href="fac.type === 'er' || fac.type === 'fire' || fac.type === 'police' ? 'tel:119' : fac.phone ? `tel:${fac.phone}` : 'tel:119'" class="call">119 신고</a>
        <a :href="directions" target="_blank" rel="noopener" class="dir">길찾기</a>
      </div>
    </template>
  </div>
</template>

<style scoped>
.facpanel {
  position: relative;
  flex: 1;
  min-height: 0;
  background: var(--web-bg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.bar {
  flex: none;
  height: 56px;
  padding: 0 10px 0 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--web-line-soft);
}
.back {
  height: 40px;
  padding: 0 10px 0 4px;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 2px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  color: var(--web-text);
  cursor: pointer;
}
.plain {
  padding-left: 10px;
  font-size: 15px;
}
.sp {
  flex: 1;
}
.x {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  border: 0;
  background: transparent;
  color: var(--web-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.state {
  margin: 24px 20px;
  font-size: 14px;
  color: var(--web-text-sub);
}
.link {
  border: 0;
  background: none;
  color: var(--web-link-blue);
  font: inherit;
  cursor: pointer;
  text-decoration: underline;
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: 12px;
}
.head {
  padding: 18px 24px 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.tag {
  align-self: flex-start;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--web-bg-tag);
  font-size: 12px;
  font-weight: 700;
  color: var(--web-text-sub);
}
.head h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
}
.addr {
  font-size: 14px;
  color: var(--web-text-sub);
}
.cards {
  padding: 14px 24px 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.card {
  padding: 14px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card small {
  font-size: 12px;
}
.card b {
  font-size: 20px;
}
.card span {
  font-size: 13px;
  color: var(--web-text-sub);
}
.card.safe { background: var(--web-grade-safe-bg); color: var(--web-grade-safe); }
.card.caution { background: var(--web-grade-danger-bg); color: var(--web-grade-danger-fg); }
.card.plain {
  background: var(--web-bg-field);
}
.card.plain small {
  color: var(--web-text-sub);
}
.card.plain b {
  font-size: 14px;
}
.missing {
  color: var(--web-arrow);
}
.rows {
  margin: 10px 24px 0;
}
.row {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 11px 0;
  border-bottom: 1px solid var(--web-line-info);
  font-size: 14px;
}
.row dt {
  color: var(--web-text-sub);
}
.row dd {
  margin: 0;
}
.row button {
  height: 30px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid var(--web-line-input);
  background: var(--web-bg);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
}
.notes {
  margin: 14px 24px 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.notes li {
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--web-grade-danger-bg);
  display: flex;
  gap: 10px;
  font-size: 14px;
}
.notes li b {
  color: var(--web-grade-danger-fg);
  white-space: nowrap;
}
.notes li p {
  margin: 0;
  line-height: 1.5;
}
.notes li.danger {
  background: var(--web-warn-bg);
}
.notes li.danger b {
  color: var(--web-text-warn);
}
.dummy {
  margin: 10px 24px 0;
  font-size: 12px;
  color: var(--web-text-mute);
}
.foot {
  flex: none;
  padding: 14px 24px 18px;
  border-top: 1px solid var(--web-line-soft);
  display: grid;
  grid-template-columns: 1fr 1.6fr;
  gap: 10px;
}
.call,
.dir {
  height: 50px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.call {
  border: 1px solid var(--web-terracotta);
  background: var(--web-bg);
  color: var(--web-terracotta);
}
.dir {
  background: var(--web-green);
  color: var(--web-white);
}
</style>
