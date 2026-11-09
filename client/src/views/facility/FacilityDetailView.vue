<script setup>
// 시설 상세 (응급실·소방서 등). 캠핑장 상세 안전점수 탭에서 들어온다 (?camp=캠핑장id)
// 데이터: GET /api/facilities/{id}?campId=
// 전화·주소는 지어내지 않는다 → 없으면 "확인 필요"
import { computed, ref, watch } from 'vue'
import RouteMap from '@/components/map/RouteMap.vue'
import DetailTopBar from '@/components/detail/DetailTopBar.vue'
import InfoRows from '@/components/detail/InfoRows.vue'
import NoteList from '@/components/detail/NoteList.vue'
import { api } from '@/utils/api'
import { FACILITY_LABEL, directionsUrl, shortSido } from '@/utils/place'

const props = defineProps({
  id: { type: Number, required: true },
  campId: { type: Number, default: null },
})

const facility = ref(null)
const error = ref('')
const copied = ref('')

async function load() {
  error.value = ''
  facility.value = null
  try {
    facility.value = await api(`/api/facilities/${props.id}`, { params: { campId: props.campId ?? undefined } })
  } catch (e) {
    error.value = e.message
  }
}
watch(() => [props.id, props.campId], load, { immediate: true })

const origin = computed(() => {
  const from = facility.value?.from
  return from ? { name: from.campName, lat: from.lat, lng: from.lng } : null
})
const targets = computed(() =>
  facility.value
    // 지도에는 아이콘만 (종류 이름은 화면 위 태그에 이미 있음). name 은 화면 읽기 프로그램용
    ? [{ id: facility.value.id, type: facility.value.type, name: FACILITY_LABEL[facility.value.type], lat: facility.value.lat, lng: facility.value.lng }]
    : [],
)
const place = computed(() => {
  const f = facility.value
  return f ? `${shortSido(f.sido)} ${f.sigungu} ${f.eupmyeon || ''}`.trim() : ''
})
const toneLabel = computed(() => (facility.value?.from?.tone === 'safe' ? '안전' : '주의'))
const directions = computed(() => (facility.value ? directionsUrl(facility.value, origin.value) : ''))

async function copyPhone() {
  try {
    await navigator.clipboard.writeText(facility.value.phone)
    copied.value = '복사했어요'
  } catch {
    copied.value = '복사하지 못했어요'
  }
}
</script>

<template>
  <div class="facility">
    <DetailTopBar :favorite="null" />

    <div v-if="error" class="state">
      <p>{{ error }}</p>
      <button type="button" class="link" @click="load">다시 시도</button>
    </div>
    <p v-else-if="!facility" class="state">불러오는 중...</p>

    <template v-else>
      <div class="map">
        <!-- 마커 색 = 출발 캠핑장의 안전점수 등급 (캠핑장 없이 들어오면 회색) -->
        <RouteMap :origin="origin" :targets="targets" :grade="facility.from?.grade" :selected-id="facility.id" dotted />
      </div>

      <main class="body">
        <span class="tag">{{ FACILITY_LABEL[facility.type] }}</span>
        <h1>{{ facility.name }}</h1>
        <p class="sub">{{ place }} · {{ facility.address || '상세 주소 확인 필요' }}</p>

        <div class="cards">
          <div v-if="facility.from" class="card time" :class="facility.from.tone">
            <small>{{ toneLabel }} · {{ facility.from.campName }}에서</small>
            <b>차로 {{ facility.from.driveMin }}분</b>
            <span>{{ facility.from.distanceKm.toFixed(1) }}km</span>
          </div>
          <div class="card">
            <small>운영 시간</small>
            <b class="hours">{{ facility.hours || '공공데이터 연동 시 표시' }}</b>
          </div>
        </div>
        <p v-if="facility.dummy" class="dummy">거리·시간·위치는 더미 데이터예요. 실제 정보가 아니에요.</p>

        <InfoRows :infos="facility.infos">
          <div class="phone">
            <dt>전화</dt>
            <dd :class="{ empty: !facility.phone }">{{ facility.phone || '확인 필요' }}</dd>
            <button v-if="facility.phone" type="button" class="copy" @click="copyPhone">복사</button>
          </div>
        </InfoRows>
        <p v-if="copied" class="copied" role="status">{{ copied }}</p>

        <NoteList class="notes" :notes="facility.notes" />
      </main>

      <footer class="foot">
        <a class="call" href="tel:119">119 신고</a>
        <a class="go" :href="directions" target="_blank" rel="noopener noreferrer">길찾기</a>
      </footer>
    </template>
  </div>
</template>

<style scoped>
.facility {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.state {
  height: 100%;
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--color-text-sub);
}
.map {
  position: relative;
  flex: 0 0 210px;
  overflow: hidden;
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}
.tag {
  padding: 4px 10px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  font-size: 12px;
  font-weight: 700;
}
h1 {
  margin: 12px 0 4px;
  font-size: 24px;
  font-weight: 900;
}
.sub {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-sub);
}
.cards {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
.card {
  flex: 1;
  min-width: 0;
  padding: 14px;
  border-radius: 14px;
  background: var(--color-bg-soft);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card small {
  font-size: 12px;
  color: var(--color-text-sub);
}
.card b {
  font-size: 22px;
}
.card b.hours {
  font-size: 15px;
}
.card span {
  font-size: 12px;
}
.card.time.safe {
  background: var(--color-safe-bg);
}
.card.time.safe small {
  color: var(--color-green);
}
.card.time.caution {
  background: var(--color-caution-bg);
}
.card.time.caution small {
  color: var(--color-caution);
}
.dummy {
  margin: 8px 0 4px;
  font-size: 11px;
  color: var(--color-caution);
}
.phone {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-track);
  font-size: 15px;
}
.phone dt {
  flex: 0 0 60px;
  font-size: 14px;
  color: var(--color-text-sub);
}
.phone dd {
  flex: 1;
  margin: 0;
}
.phone dd.empty {
  color: var(--color-text-sub);
}
.copy {
  height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: 16px;
  background: var(--color-bg-soft);
  font-size: 13px;
  cursor: pointer;
}
.copied {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-text-sub);
}
.notes {
  margin-top: 14px;
}
.foot {
  flex: none;
  display: flex;
  gap: 10px;
  padding: 12px 16px 20px;
  border-top: 1px solid var(--color-track);
}
.call,
.go {
  flex: 1;
  height: 50px;
  border-radius: 25px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-card);
  font-size: 16px;
  font-weight: 700;
}
.call {
  background: var(--color-danger);
}
.go {
  background: var(--color-text);
}
.link {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
</style>
