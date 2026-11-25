<script setup>
// 웹 관광지 상세: 캠핑장 상세의 관광지 탭에서 들어온다 (/web/tours/:id?camp=캠핑장id)
// 데이터: GET /api/tours/{id}?campId=   (모바일 TourDetailView 와 같은 API)
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import WebSubPage from '@/components/web/WebSubPage.vue'
import { goUp } from '@/utils/backStack'
import { api } from '@/utils/api'
import { directionsUrl, shortSido } from '@/utils/place'

const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.id))
const campId = computed(() => (route.query.camp ? Number(route.query.camp) : null))

const tour = ref(null)
const error = ref('')

async function load() {
  error.value = ''
  tour.value = null
  try {
    tour.value = await api(`/api/tours/${id.value}`, { params: { campId: campId.value ?? undefined } })
  } catch (e) {
    error.value = e.message
  }
}
watch([id, campId], load, { immediate: true })

const LEVEL_LABEL = { info: '안내', caution: '주의', warn: '주의', danger: '위험' }

const place = computed(() => {
  const t = tour.value
  return t ? `${shortSido(t.sido)} ${t.sigungu} ${t.eupmyeon || ''}`.trim() : ''
})
const driveText = computed(() => (tour.value?.from ? `${tour.value.from.campName}에서 차로 약 ${tour.value.from.driveMin}분` : ''))
const directions = computed(() => {
  const t = tour.value
  if (!t) return ''
  const f = t.from
  return directionsUrl(t, f ? { name: f.campName, lat: f.lat, lng: f.lng } : null)
})
// 돌아갈 곳: 캠핑장이 있으면 그 캠핑장 상세, 없으면 탐색
const backTo = computed(() => (tour.value?.from ? `/web/camps/${tour.value.from.campId}?tab=tour` : '/web/explore'))
</script>

<template>
  <WebSubPage eyebrow="관광지" :title="tour?.name || '관광지 상세'" :desc="place">
    <template #actions>
      <RouterLink :to="backTo" class="back" @click.capture.prevent="goUp(router, backTo)">‹ 캠핑장으로 돌아가기</RouterLink>
    </template>

    <p v-if="error" class="state">{{ error }} <button type="button" class="retry" @click="load">다시 시도</button></p>
    <p v-else-if="!tour" class="state">불러오는 중...</p>

    <div v-else class="grid">
      <section class="card">
        <div class="photo"><span>{{ tour.name }} 대표 사진 (준비 중)</span></div>
        <div class="tags">
          <span class="tag">{{ tour.categoryLabel }}</span>
          <span v-if="tour.popularityRank" class="tag rank">인기 {{ tour.popularityRank }}위</span>
        </div>
        <h2>{{ tour.name }}</h2>
        <p class="sub">{{ place }}<template v-if="driveText"> · {{ driveText }}</template></p>
        <p v-if="tour.dummy" class="dummy">시간·위치는 더미 데이터예요. 실제 정보가 아니에요.</p>

        <template v-if="tour.notes?.length">
          <h3>가기 전에 알아두세요</h3>
          <ul class="notes">
            <li v-for="(n, i) in tour.notes" :key="i" :class="n.level">
              <b>{{ LEVEL_LABEL[n.level] || '안내' }}</b>
              <p>{{ n.body }}</p>
            </li>
          </ul>
        </template>
      </section>

      <aside class="card side">
        <h3>정보</h3>
        <dl class="rows">
          <template v-for="(info, i) in tour.infos" :key="i">
            <div v-if="info.label" class="row">
              <dt>{{ info.label }}</dt>
              <dd :class="{ empty: !info.value }">{{ info.value || '확인 필요' }}</dd>
            </div>
            <p v-else class="plain">{{ info.value }}</p>
          </template>
        </dl>
        <a class="go" :href="directions" target="_blank" rel="noopener noreferrer">
          {{ tour.from ? '캠핑장에서 길찾기' : '길찾기' }}
        </a>
        <RouterLink :to="backTo" class="other" @click.capture.prevent="goUp(router, backTo)">다른 관광지 보기</RouterLink>
      </aside>
    </div>
  </WebSubPage>
</template>

<style scoped>
.back {
  height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid var(--web-line-ghost);
  background: var(--web-white);
  color: var(--web-green);
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
}
.state {
  text-align: center;
  color: var(--web-text-sub);
  padding: 80px 0;
}
.retry {
  border: 0;
  background: none;
  color: var(--web-green);
  font-weight: 700;
  text-decoration: underline;
  cursor: pointer;
}
.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 24px;
  align-items: start;
}
.card {
  background: var(--web-bg);
  border: 1px solid var(--web-border-card);
  border-radius: 16px;
  padding: 28px;
}
.photo {
  height: 280px;
  border-radius: 12px;
  background: var(--web-bg-hero);
  color: var(--web-text-photo);
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}
.tags {
  display: flex;
  gap: 6px;
}
.tag {
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--web-bg-chip);
  font-size: 13px;
  font-weight: 700;
}
.tag.rank {
  background: var(--web-green);
  color: var(--web-white);
}
h2 {
  margin: 12px 0 4px;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.5px;
}
h3 {
  margin: 24px 0 10px;
  font-size: 17px;
  font-weight: 800;
}
.side h3 {
  margin-top: 0;
}
.sub {
  margin: 0;
  color: var(--web-text-sub);
  font-size: 15px;
}
.dummy {
  margin: 12px 0 0;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--web-grade-danger-bg);
  color: var(--web-grade-danger-fg);
  font-size: 13px;
}
.notes {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.notes li {
  padding: 12px 16px;
  border-radius: 10px;
  background: var(--web-bg-prep);
  display: flex;
  gap: 12px;
  align-items: baseline;
}
.notes li.caution,
.notes li.warn,
.notes li.danger {
  background: var(--web-warn-bg);
}
.notes b {
  flex: none;
  font-size: 13px;
  color: var(--web-green);
}
.notes p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--web-text-prep);
}
.rows {
  margin: 0;
}
.row {
  display: flex;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--web-line-info);
  font-size: 14px;
}
.row dt {
  flex: none;
  width: 70px;
  color: var(--web-text-sub);
}
.row dd {
  margin: 0;
  flex: 1;
  line-height: 1.5;
}
.row dd.empty {
  color: var(--web-text-mute);
}
.plain {
  margin: 8px 0;
  font-size: 14px;
}
.go,
.other {
  margin-top: 14px;
  height: 50px;
  border-radius: 999px;
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.go {
  background: var(--web-green);
  color: var(--web-white);
}
.other {
  margin-top: 8px;
  border: 1px solid var(--web-line-ghost);
  color: var(--web-text);
  font-weight: 500;
}
</style>
