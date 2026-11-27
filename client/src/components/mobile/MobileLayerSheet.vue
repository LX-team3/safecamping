<script setup>
// "지도 설정" 시트 (탐색 · 내 주변 · 차박 지도 오른쪽 위 레이어 버튼)
//  - 위: 지도 종류 지도 · 스카이뷰 (utils/mapType.js — 세 지도가 같이 씀). 미리보기 그림으로 구분
//  - 아래: 안전 시설 켜고 끄기
// 줄 목록은 서버(GET /api/nearby/layers)가 정본 — 데이터가 준비된 시설(ready)만 보인다. 대피소는 데이터가 없어 숨김
import { nextTick, onMounted, ref } from 'vue'
import MobileBottomSheet from '@/components/mobile/MobileBottomSheet.vue'
import { MAP_TYPES, drawSkyPreview, mapType } from '@/utils/mapType'
import { loadKakaoMap } from '@/utils/kakaoMap'
import { facilityIcon } from '@/utils/facilityIcon'

defineProps({
  layers: { type: Array, required: true }, // [{ code, icon, label, desc, ready }]
  on: { type: Object, required: true }, // { er: true, ... } 켜짐 여부
  // 판정 표시 (차박 지도에서만 넘김): 목록 utils/chabak.js LAYER_CHIPS, 켜짐 여부 { no: true, ... } (여러 개 가능)
  verdictLayers: { type: Array, default: null },
  verdictOn: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['close', 'toggle', 'verdict-layer'])

function pickType(key) {
  mapType.value = key
}

// 미리보기 그림 (글자 없이)
//  - 지도: 아래 템플릿의 간단한 그림 (땅 · 숲 · 강 · 도로)
//  - 스카이뷰: 카카오 위성사진 (키가 없으면 빈 칸)
const skyEl = ref(null)
onMounted(async () => {
  let kakao = null
  try {
    kakao = await loadKakaoMap()
  } catch {
    kakao = null
  }
  await nextTick() // 칸 크기가 정해진 뒤에 그린다 (정적 지도는 그리는 순간의 칸 크기로 그림을 만든다)
  // v-for 안의 ref 는 배열로 들어온다 → 첫 칸
  const el = Array.isArray(skyEl.value) ? skyEl.value[0] : skyEl.value
  drawSkyPreview(kakao, el)
})
</script>

<template>
  <MobileBottomSheet title="지도 설정" @close="emit('close')">
    <!-- 지도 종류: 둘 중 하나만 고르는 것이라 라디오 묶음. 미리보기 그림 + 이름 -->
    <div class="types" role="radiogroup" aria-label="지도 종류">
      <button v-for="t in MAP_TYPES" :key="t.key" type="button" role="radio" class="type"
        :class="{ on: mapType === t.key }" :aria-checked="mapType === t.key" @click="pickType(t.key)">
        <!-- 미리보기 그림 (장식이라 스크린리더는 이름만 읽음) -->
        <span v-if="t.key === 'roadmap'" class="thumb" aria-hidden="true">
          <svg viewBox="0 0 160 72" preserveAspectRatio="xMidYMid slice" width="100%" height="100%">
            <rect width="160" height="72" class="land" />
            <path d="M0 0h70c-8 18-30 22-42 34C18 44 8 40 0 46z" class="forest" />
            <path d="M118 72c4-20 20-30 42-34v34z" class="forest" />
            <path d="M-4 58C30 50 52 62 84 46s52-30 80-26" class="river" />
            <path d="M0 22L160 52" class="road" />
            <path d="M96 0L78 72" class="road" />
          </svg>
        </span>
        <!-- 카카오 정적 지도(위성사진)가 이 칸 안에 그림을 넣는다 -->
        <span v-else ref="skyEl" class="thumb" aria-hidden="true" />
        <span class="name">{{ t.label }}</span>
      </button>
    </div>

    <!-- 갈 수 있는 곳 (차박 지도만: 차박 후보지 · 등록 야영장): 여러 개를 같이 켤 수 있다. 색 점은 판정 색 (styles/tokens.css --color-verdict-*) -->
    <template v-if="verdictLayers">
      <h3 class="sub">갈 수 있는 곳</h3>
      <ul class="rows">
        <li v-for="v in verdictLayers" :key="v.type" class="row">
          <span class="icon" aria-hidden="true"><i class="dot" :style="{ background: `var(${v.colorVar})` }" /></span>
          <div class="text">
            <b>{{ v.label }}</b>
            <span>{{ v.note }}</span>
          </div>
          <button type="button" role="switch" class="switch" :class="{ on: verdictOn[v.type] }"
            :aria-checked="!!verdictOn[v.type]" :aria-label="`${v.label} 지도에 보이기`"
            @click="emit('verdict-layer', v.type)">
            <i />
          </button>
        </li>
      </ul>
    </template>

    <h3 class="sub">안전 시설</h3>
    <ul class="rows">
      <!-- 데이터가 없는 시설(ready=false, 지금은 대피소)은 켜도 아무것도 안 나와서 줄을 숨긴다 (웹 칩과 같음) -->
      <li v-for="l in layers.filter((x) => x.ready)" :key="l.code" class="row">
        <!-- 아이콘: 지도에 뜨는 시설 배지와 같은 모양·색 (utils/facilityIcon.js, styles/base.css .fac-badge.plain). 설명 글은 뺐다 -->
        <span class="fac-badge plain lbadge" :class="`t-${l.code}`" aria-hidden="true" v-html="facilityIcon(l.code)" />
        <div class="text">
          <b>{{ l.label }}</b>
        </div>
        <button type="button" role="switch" class="switch" :class="{ on: on[l.code] }" :aria-checked="!!on[l.code]"
          :aria-label="`${l.label} 지도에 보이기`" @click="emit('toggle', l.code)">
          <i />
        </button>
      </li>
    </ul>
  </MobileBottomSheet>
</template>

<style scoped>
.types {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
/* 카드: 위 미리보기 그림 + 아래 이름. 고른 쪽은 검은 테두리 + 굵은 글씨 */
.type {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  color: var(--color-text-sub);
  display: flex;
  flex-direction: column;
  gap: 6px;
  cursor: pointer;
}
.thumb {
  display: block;
  height: 72px;
  border: 2px solid var(--color-border);
  border-radius: var(--radius-input);
  overflow: hidden; /* 정적 지도 그림이 둥근 모서리 밖으로 안 나가게 */
  background: var(--color-map-bg);
  transition: border-color 0.15s;
  /* 카카오 정적 지도는 그림을 카카오맵 사이트 링크로 감싼다 → 눌러도 링크가 아니라 이 카드가 눌리게 */
  pointer-events: none;
}
/* 기본 지도 미리보기 그림 색 (styles/tokens.css 변수만 사용) */
.thumb svg {
  display: block;
}
.land {
  fill: var(--color-map-bg);
}
.forest {
  fill: var(--color-safe-bg);
}
.river {
  fill: none;
  stroke: var(--color-route-core);
  stroke-width: 8;
  stroke-linecap: round;
}
.road {
  fill: none;
  stroke: var(--color-card);
  stroke-width: 5;
}
.type.on .thumb {
  border-color: var(--color-text);
}
.name {
  font-size: 14px;
  text-align: center;
}
.type.on .name {
  color: var(--color-text);
  font-weight: 700;
}
.sub {
  margin: 20px 0 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-sub);
}
.rows {
  margin: 4px 0 0;
  padding: 0;
  list-style: none;
}
.row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-line);
}
.icon {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--color-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
}
.lbadge {
  --fac-size: 36px;
  box-shadow: none; /* 지도 위가 아니라 그림자 없이 */
}
.icon .dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}
.text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.text b {
  font-size: 15px;
}
.text span {
  font-size: 12px;
  color: var(--color-text-sub);
}
.switch {
  flex: none;
  width: 50px;
  height: 30px;
  padding: 3px;
  border: none;
  border-radius: 15px;
  background: var(--color-track);
  display: flex;
  cursor: pointer;
  transition: background 0.15s;
}
.switch i {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-card);
  transition: transform 0.15s;
}
.switch.on {
  background: var(--color-primary);
}
.switch.on i {
  transform: translateX(20px);
}
</style>
