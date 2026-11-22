<script setup>
// 날씨 아이콘 (캠핑 기간 날씨 카드 · 웹/앱 같이 씀)
// 사용: <WeatherIcon :sky="d.sky" :precip="d.precip" />   → 서버 forecast.days 의 sky(맑음·구름많음·흐림)와 precip(없음·비·비/눈·눈·소나기)
// 강수가 있으면 하늘보다 강수가 우선이다 (눈이 오는 날은 눈 아이콘). 둘 다 없으면 아무것도 그리지 않는다
// 색은 styles/tokens.css 의 --color-wx-*-ic 변수. 종류 이름(kind)은 weatherKind 로 밖에서도 쓸 수 있다(카드 바탕색)
import { computed } from 'vue'
import { weatherKind } from '@/utils/weather'

const props = defineProps({
  sky: { type: String, default: null },
  precip: { type: String, default: null },
  size: { type: Number, default: 24 },
})
const kind = computed(() => weatherKind(props.sky, props.precip))
</script>

<template>
  <svg v-if="kind" :class="['wxi', `wxi-${kind}`]" :width="size" :height="size" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <!-- 맑음: 해 -->
    <template v-if="kind === 'clear'">
      <circle cx="12" cy="12" r="4.2" fill="currentColor" stroke="none" />
      <path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7" />
    </template>
    <!-- 구름많음: 해 + 구름 -->
    <template v-else-if="kind === 'partly'">
      <circle cx="8.5" cy="8.5" r="3" fill="currentColor" stroke="none" />
      <path d="M8.5 2.8v1.4M2.8 8.5h1.4M4.5 4.5l1 1M12.5 4.5l-1 1" />
      <path d="M8 19h9.2a3.4 3.4 0 0 0 .4-6.8A5 5 0 0 0 8.2 13 3 3 0 0 0 8 19z" class="cloud" />
    </template>
    <!-- 흐림: 구름 -->
    <template v-else-if="kind === 'cloud'">
      <path d="M6.8 18.5h10.4a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.1 11.6a3.5 3.5 0 0 0 .7 6.9z" fill="currentColor" fill-opacity=".18" />
    </template>
    <!-- 비 · 소나기: 구름 + 빗줄기 -->
    <template v-else-if="kind === 'rain'">
      <path d="M6.8 14.5h10.4a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.1 7.6a3.5 3.5 0 0 0 .7 6.9z" fill="currentColor" fill-opacity=".18" />
      <path d="M8.5 17.5l-1 2.5M12.5 17.5l-1 2.5M16.5 17.5l-1 2.5" />
    </template>
    <!-- 눈 · 비/눈: 구름 + 눈송이 점 -->
    <template v-else>
      <path d="M6.8 14.5h10.4a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.1 7.6a3.5 3.5 0 0 0 .7 6.9z" fill="currentColor" fill-opacity=".18" />
      <circle cx="8.5" cy="18.5" r="1" fill="currentColor" stroke="none" />
      <circle cx="12.5" cy="20" r="1" fill="currentColor" stroke="none" />
      <circle cx="16.5" cy="18.5" r="1" fill="currentColor" stroke="none" />
    </template>
  </svg>
</template>

<style scoped>
.wxi {
  flex: none;
}
.wxi-clear { color: var(--color-wx-clear-ic); }
.wxi-partly { color: var(--color-wx-partly-ic); }
.wxi-partly .cloud { color: var(--color-wx-cloud-ic); fill: var(--color-wx-cloud-bg); }
.wxi-cloud { color: var(--color-wx-cloud-ic); }
.wxi-rain { color: var(--color-wx-rain-ic); }
.wxi-snow { color: var(--color-wx-snow-ic); }
</style>
