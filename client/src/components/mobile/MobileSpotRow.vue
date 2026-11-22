<script setup>
// 차박 목록 한 줄 (탐색 목록 CampRow 와 같은 틀): 순위 · 사진 · 이름 · 판정 딱지 + 지역 · 안전 배지 · 응급실 시간 · ›
//  - 등록 야영장: 고캠핑 사진, "응급실 차로 N분" (camp.er_drive_min — 캠핑장 목록과 같은 값)
//  - 차박 후보지: 사진이 없어 그 자리 스카이뷰(카카오 정적 지도, 위성사진). 응급실 시간은 직선거리 어림값이라 줄에 안 보임
// 줄을 누르면 open → 화면이 지도를 그 자리로 옮기고 판정 카드를 띄운다
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { loadKakaoMap } from '@/utils/kakaoMap'
import { GRADE_LABEL, VERDICT } from '@/utils/chabak'

const props = defineProps({
  item: { type: Object, required: true }, // { kind: open·camp, name, area, imageUrl, lat, lng, score, grade, erDriveMin }
  rank: { type: Number, required: true },
})
defineEmits(['open'])

const verdict = props.item.kind === 'camp' ? '야영 가능' : '제한 없음'

// ---- 스카이뷰 작은 사진 (후보지만) ----
// 목록이 길어서 화면에 보일 때만 그린다 (정적 지도 = 카카오 이미지 요청 한 번)
const skyEl = ref(null)
const noSky = ref(false) // 카카오 키가 없으면 그림 대신 아이콘
let observer = null

onMounted(() => {
  if (props.item.imageUrl || !skyEl.value) return
  observer = new IntersectionObserver(async ([entry]) => {
    if (!entry.isIntersecting) return
    observer.disconnect()
    const kakao = await loadKakaoMap().catch(() => null)
    if (!kakao || !skyEl.value) {
      noSky.value = true
      return
    }
    const position = new kakao.maps.LatLng(props.item.lat, props.item.lng)
    new kakao.maps.StaticMap(skyEl.value, {
      center: position,
      level: 3,
      mapTypeId: kakao.maps.MapTypeId.SKYVIEW, // 자리가 사진 가운데 (기본 핀은 74px 칸에 비해 너무 커서 안 붙임)
    })
  })
  observer.observe(skyEl.value)
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <!-- 클래스 이름이 row 가 아닌 이유: 부모(ChabakView)의 scoped .row(높이 48px)가 부품 맨 바깥 요소에도 걸려서 줄이 눌림 -->
  <li class="spot-row">
    <button type="button" class="main" @click="$emit('open', item)">
      <span class="rank">{{ rank }}</span>
      <span class="thumb">
        <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" loading="lazy" />
        <span v-else ref="skyEl" class="sky" :aria-label="`${item.name} 위성사진`">
          <!-- 카카오 키가 없을 때: 지도 그림 -->
          <svg v-if="noSky" viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor"
            stroke-width="1.6" stroke-linejoin="round" aria-hidden="true">
            <path d="M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z" />
            <path d="M9 4v14M15 6v14" />
          </svg>
        </span>
      </span>
      <span class="info">
        <b class="name">{{ item.name }}</b>
        <span class="area">
          <span class="verdict" :style="{ background: `var(${VERDICT[verdict].colorVar})` }">{{ verdict }}</span>
          <span class="area-text">{{ item.area }}</span>
        </span>
        <span class="meta">
          <span class="badge" :class="item.grade ?? 'unknown'">
            <i />{{ item.score != null ? `${GRADE_LABEL[item.grade]} ${item.score}` : '점수 없음' }}
          </span>
          <!-- 응급실 시간은 등록 야영장만 (카카오 차 소요시간). 후보지는 직선거리 어림값이라 보이지 않고, 안전 점수에는 이미 들어 있다 -->
          <span v-if="item.kind === 'camp' && item.erDriveMin != null">응급실 차로 {{ item.erDriveMin }}분</span>
        </span>
      </span>
      <span class="arrow" aria-hidden="true">›</span>
    </button>
  </li>
</template>

<style scoped>
.spot-row {
  border-bottom: 1px solid var(--color-track);
}
.main {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 0;
  border: none;
  background: none;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.rank {
  flex: 0 0 16px;
  font-size: 17px;
  font-weight: 900;
}
.thumb {
  flex: none;
  width: 74px;
  height: 74px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--color-map-bg);
  color: var(--color-text-sub);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.sky {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 카카오 정적 지도가 안에 넣는 그림을 칸에 꽉 채운다 */
.sky :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.name {
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.area {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  color: var(--color-text-sub);
}
.area-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 판정 딱지: 지도 점과 같은 색 (제한 없음 초록 · 야영 가능 파랑) */
.verdict {
  flex: none;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  color: var(--color-card);
}
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-sub);
}
/* 안전 배지: 탐색 목록(CampRow)과 같은 모양·색 */
.badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 700;
  background: var(--color-bg-soft);
  color: var(--color-text-sub);
}
.badge i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}
.badge.safe {
  background: var(--color-safe-bg);
  color: var(--color-green);
}
.badge.caution {
  background: var(--color-caution-bg);
  color: var(--color-caution);
}
.badge.danger {
  background: var(--color-warn-bg);
  color: var(--color-danger);
}
.arrow {
  flex: none;
  font-size: 20px;
  color: var(--color-arrow);
}
</style>
