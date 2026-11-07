<script setup>
// 관광지 상세. 캠핑장 상세 관광지 탭에서 들어온다 (?camp=캠핑장id)
// 데이터: GET /api/tours/{id}?campId=
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import DetailTopBar from '@/components/detail/DetailTopBar.vue'
import PhotoBox from '@/components/detail/PhotoBox.vue'
import InfoRows from '@/components/detail/InfoRows.vue'
import NoteList from '@/components/detail/NoteList.vue'
import { api } from '@/utils/api'
import { directionsUrl, shortSido } from '@/utils/place'
import { useFavoriteStore } from '@/stores/favorite'

const props = defineProps({
  id: { type: Number, required: true },
  campId: { type: Number, default: null },
})

const router = useRouter()
const favorite = useFavoriteStore()

const tour = ref(null)
const error = ref('')

async function load() {
  error.value = ''
  tour.value = null
  try {
    tour.value = await api(`/api/tours/${props.id}`, { params: { campId: props.campId ?? undefined } })
  } catch (e) {
    error.value = e.message
  }
}
watch(() => [props.id, props.campId], load, { immediate: true })

// 하트는 캠핑장과 같은 저장소를 쓰므로 id 가 겹치지 않게 앞에 표시를 붙인다
const favoriteKey = computed(() => `tour:${props.id}`)

const subLine = computed(() => {
  const t = tour.value
  if (!t) return ''
  const place = `${shortSido(t.sido)} ${t.sigungu} ${t.eupmyeon || ''}`.trim()
  return t.from ? `${place} · ${t.from.campName}에서 차로 약 ${t.from.driveMin}분` : place
})

const directions = computed(() => {
  const from = tour.value?.from
  return tour.value ? directionsUrl(tour.value, from ? { name: from.campName, lat: from.lat, lng: from.lng } : null) : ''
})

function otherTours() {
  if (tour.value?.from) router.push(`/camps/${tour.value.from.campId}?tab=tour`)
  else router.push('/explore')
}
</script>

<template>
  <div class="tour">
    <DetailTopBar :favorite="favorite.has(favoriteKey)" @favorite="favorite.toggle(favoriteKey)" />

    <div v-if="error" class="state">
      <p>{{ error }}</p>
      <button type="button" class="link" @click="load">다시 시도</button>
    </div>
    <p v-else-if="!tour" class="state">불러오는 중...</p>

    <template v-else>
      <main class="body">
        <PhotoBox square :alt="tour.name" :caption="`${tour.name} 대표 사진`" />
        <div class="text">
          <div class="tags">
            <span class="tag">{{ tour.categoryLabel }}</span>
            <span v-if="tour.popularityRank" class="tag rank">인기 {{ tour.popularityRank }}위</span>
          </div>
          <h1>{{ tour.name }}</h1>
          <p class="sub">{{ subLine }}</p>
          <p v-if="tour.dummy" class="dummy">시간·위치는 더미 데이터예요. 실제 정보가 아니에요.</p>

          <template v-if="tour.notes.length">
            <h2>가기 전에 알아두세요</h2>
            <NoteList :notes="tour.notes" />
          </template>

          <InfoRows class="infos" :infos="tour.infos" />
        </div>
      </main>

      <footer class="foot">
        <button type="button" class="other" @click="otherTours">다른 관광지</button>
        <a class="go" :href="directions" target="_blank" rel="noopener noreferrer">
          {{ tour.from ? '캠핑장에서 길찾기' : '길찾기' }}
        </a>
      </footer>
    </template>
  </div>
</template>

<style scoped>
.tour {
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
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.text {
  padding: 16px;
}
.tags {
  display: flex;
  gap: 6px;
}
.tag {
  padding: 4px 10px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  font-size: 12px;
  font-weight: 700;
}
.tag.rank {
  background: var(--color-text);
  color: var(--color-card);
}
h1 {
  margin: 12px 0 4px;
  font-size: 24px;
  font-weight: 900;
}
h2 {
  margin: 20px 0 10px;
  font-size: 16px;
  font-weight: 900;
}
.sub {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-sub);
}
.dummy {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--color-caution);
}
.infos {
  margin-top: 12px;
}
.foot {
  flex: none;
  display: flex;
  gap: 10px;
  padding: 12px 16px 20px;
  border-top: 1px solid var(--color-track);
}
.other,
.go {
  height: 50px;
  border-radius: 25px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.other {
  flex: 0 0 40%;
  border: 1px solid var(--color-text);
  background: var(--color-card);
}
.go {
  flex: 1;
  background: var(--color-text);
  color: var(--color-card);
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
