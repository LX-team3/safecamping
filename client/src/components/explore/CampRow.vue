<script setup>
// 탐색 목록 한 줄: 순위 · 사진 · 이름 · 지역 · 안전 배지 · 응급실 시간 · 하트
defineProps({
  camp: { type: Object, required: true },
  rank: { type: Number, default: 0 }, // 0 이면 순위 숫자 안 보임 (추천이 아닌 전체 목록)
  favorite: { type: Boolean, default: false },
})
defineEmits(['open', 'favorite'])

const GRADE_LABEL = { safe: '안전', caution: '주의', danger: '위험', unknown: '점수 없음' }
</script>

<template>
  <li class="row">
    <!-- 줄을 누르면 캠핑장 상세 -->
    <button type="button" class="main" @click="$emit('open', camp.id)">
      <span v-if="rank" class="rank">{{ rank }}</span>
      <span class="thumb">
        <img v-if="camp.imageUrl" :src="camp.imageUrl" :alt="camp.name" />
        <!-- 사진이 없을 때: 텐트 그림 -->
        <svg v-else viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="1.6"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M3 20L12 4l9 16z" />
          <path d="M12 20v-6" />
        </svg>
      </span>
      <span class="info">
        <b class="name">{{ camp.name }}</b>
        <span class="area">{{ camp.sigungu }} {{ camp.eupmyeon }}</span>
        <span class="meta">
          <span class="badge" :class="camp.grade"><i />{{ GRADE_LABEL[camp.grade] }} {{ camp.safetyScore ?? '' }}</span>
          <span v-if="camp.erDriveMin != null">응급실 차로 {{ camp.erDriveMin }}분</span>
        </span>
      </span>
    </button>
    <button type="button" class="heart" :class="{ on: favorite }" :aria-pressed="favorite"
      :aria-label="favorite ? `${camp.name} 저장 취소` : `${camp.name} 저장`" @click="$emit('favorite', camp.id)">
      <svg viewBox="0 0 24 24" width="22" height="22" :fill="favorite ? 'currentColor' : 'none'" stroke="currentColor"
        stroke-width="2" stroke-linejoin="round" aria-hidden="true">
        <path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z" />
      </svg>
    </button>
  </li>
</template>

<style scoped>
.row {
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-track);
}
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 0;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
}
.rank {
  flex: 0 0 14px;
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
  display: flex;
  align-items: center;
  justify-content: center;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.name {
  font-size: 16px;
}
.area {
  font-size: 13px;
  color: var(--color-text-sub);
}
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-sub);
}
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
  color: var(--color-caution);
}
.badge.danger {
  background: var(--color-warn-bg);
  color: var(--color-danger);
}
.heart {
  flex: none;
  width: 44px;
  height: 44px;
  border: none;
  background: none;
  cursor: pointer;
}
.heart.on {
  color: var(--color-danger);
}
</style>
