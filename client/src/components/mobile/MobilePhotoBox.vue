<script setup>
// 대표 사진 자리. 사진이 없으면 회색 칸에 그림과 안내 글씨
defineProps({
  src: { type: String, default: '' },
  alt: { type: String, required: true },
  caption: { type: String, default: '' },
  square: { type: Boolean, default: false }, // 화면 맨 위에 꽉 차게 (모서리 둥글림 없음)
})
</script>

<template>
  <div class="photo" :class="{ square }">
    <img v-if="src" :src="src" :alt="alt" />
    <template v-else>
      <svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor" stroke-width="1.6"
        stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M3 20L12 4l9 16z" />
        <path d="M12 20v-6" />
      </svg>
      <span>{{ caption }}</span>
    </template>
  </div>
</template>

<style scoped>
.photo {
  height: 170px;
  border-radius: 14px;
  overflow: hidden;
  background: var(--color-map-bg);
  color: var(--color-text-sub);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
}
.photo.square {
  height: 240px;
  border-radius: 0;
}
img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
