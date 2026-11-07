<script setup>
// "어디로 캠핑을 떠나시나요?" 지역 선택 시트
// 전국(시·도 17곳) → 시·도를 누르면 그 시·군·구 → 하나 고르고 "이 지역 캠핑장 보기"
// 데이터: GET /api/regions/sidos, /api/regions/sidos/{id}/sigungus, /api/regions/sigungus?q= (검색)
import { computed, onMounted, ref, watch } from 'vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { api } from '@/utils/api'
import { shortSido } from '@/utils/place'
import { useTripStore } from '@/stores/trip'

const emit = defineEmits(['close', 'apply'])
const trip = useTripStore()

const sidos = ref([])
const currentSido = ref(trip.state.sido) // null 이면 전국 화면
const sigungus = ref([])
const picked = ref(trip.state.sigungu) // 고른 시·군·구 (아직 적용 전)
const query = ref('')
const results = ref([]) // 검색 결과
const error = ref('')

onMounted(async () => {
  try {
    sidos.value = await api('/api/regions/sidos')
    if (currentSido.value) await openSido(currentSido.value)
  } catch (e) {
    error.value = e.message
  }
})

async function openSido(sido) {
  error.value = ''
  currentSido.value = sido
  query.value = ''
  try {
    sigungus.value = await api(`/api/regions/sidos/${sido.id}/sigungus`)
  } catch (e) {
    error.value = e.message
  }
}

function toNation() {
  currentSido.value = null
  picked.value = null
}

// 검색: 입력을 멈추고 0.3초 뒤에 부른다 (한 글자마다 서버를 부르지 않게)
let timer = null
watch(query, (q) => {
  clearTimeout(timer)
  if (!q.trim()) {
    results.value = []
    return
  }
  timer = setTimeout(async () => {
    try {
      results.value = await api('/api/regions/sigungus', { params: { q } })
    } catch (e) {
      error.value = e.message
    }
  }, 300)
})

function pickFromSearch(g) {
  const sido = sidos.value.find((s) => s.name === g.sido)
  if (sido) openSido(sido)
  picked.value = g
}

const countLabel = computed(() =>
  currentSido.value ? `시·군·구 ${sigungus.value.length}곳` : `시·도 ${sidos.value.length}곳`,
)

// 아래 안내 문구: 무엇을 골랐는지 + 안심 데이터가 있는지
const footText = computed(() => {
  if (picked.value) {
    const where = `${shortSido(currentSido.value?.name)} ${picked.value.name}`
    return picked.value.safetyReady
      ? `${where} · 안심 점수를 바로 계산할 수 있어요`
      : `${where} · 안심 데이터를 준비하고 있어요`
  }
  if (currentSido.value) return `${currentSido.value.name} 전체 캠핑장을 보여 드려요`
  return '전국 캠핑장을 보여 드려요'
})

function apply() {
  // 시·군·구를 안 골랐으면 시·도 전체(또는 전국)
  trip.setRegion(currentSido.value, picked.value)
  emit('apply')
}
</script>

<template>
  <BottomSheet title="어디로 캠핑을 떠나시나요?" @close="emit('close')">
    <!-- TODO: 안심 데이터가 있는 지역이 늘면 서버(is_safety_ready)에서 문구를 만든다 -->
    <p class="notice">지금은 <b>강원 인제군 시범 서비스</b>예요. 다른 지역은 안전점수 데이터를 준비하고 있어요.</p>

    <label class="search">
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"
        stroke-linecap="round" aria-hidden="true">
        <circle cx="11" cy="11" r="7" />
        <path d="M21 21l-4.5-4.5" />
      </svg>
      <input v-model="query" type="search" placeholder="시·군·구 검색" aria-label="시·군·구 검색" />
    </label>

    <p v-if="error" class="state" role="alert">{{ error }}</p>

    <!-- 검색 중이면 검색 결과 -->
    <template v-if="query.trim()">
      <p v-if="!results.length" class="state">찾는 시·군·구가 없어요.</p>
      <ul v-else class="list">
        <li v-for="g in results" :key="g.id">
          <button type="button" class="item" :class="{ on: picked?.id === g.id }" @click="pickFromSearch(g)">
            <span>{{ g.name }} <small>{{ g.sido }}</small></span>
            <span v-if="g.safetyReady" class="ready">안심 데이터 있음</span>
          </button>
        </li>
      </ul>
    </template>

    <template v-else>
      <div class="crumb">
        <span class="path">
          <button type="button" class="nation" :class="{ on: !currentSido }" @click="toNation">전국</button>
          <template v-if="currentSido">
            <span aria-hidden="true">›</span><b>{{ currentSido.name }}</b>
          </template>
        </span>
        <span class="count">{{ countLabel }}</span>
      </div>

      <!-- 전국: 시·도 두 줄 -->
      <ul v-if="!currentSido" class="grid">
        <li v-for="s in sidos" :key="s.id">
          <button type="button" class="item" @click="openSido(s)">{{ s.name }}</button>
        </li>
      </ul>

      <!-- 시·도 안: 시·군·구 한 줄씩 -->
      <template v-else>
        <p v-if="!sigungus.length" class="state">이 지역은 시·군·구 목록을 준비하고 있어요.</p>
        <ul v-else class="list">
          <li v-for="g in sigungus" :key="g.id">
            <button type="button" class="item" :class="{ on: picked?.id === g.id }" :aria-pressed="picked?.id === g.id"
              @click="picked = picked?.id === g.id ? null : g">
              <span>{{ g.name }}</span>
              <span v-if="g.safetyReady" class="ready">안심 데이터 있음</span>
            </button>
          </li>
        </ul>
      </template>
    </template>

    <template #footer>
      <p class="foot-text">{{ footText }}</p>
      <button type="button" class="apply" @click="apply">이 지역 캠핑장 보기</button>
    </template>
  </BottomSheet>
</template>

<style scoped>
.notice {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-sub);
}
.notice b {
  color: var(--color-text);
}
.search {
  height: 48px;
  margin-top: 14px;
  padding: 0 16px;
  border-radius: 24px;
  background: var(--color-bg-soft);
  display: flex;
  align-items: center;
  gap: 10px;
}
.search input {
  flex: 1;
  min-width: 0;
  border: none;
  background: none;
  font-size: 15px;
  outline: none;
}
.crumb {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 14px 0 10px;
}
.path {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.nation {
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-card);
  font-size: 13px;
  cursor: pointer;
}
.count {
  font-size: 12px;
  color: var(--color-text-sub);
}
.grid,
.list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}
.grid {
  grid-template-columns: 1fr 1fr;
}
.item {
  width: 100%;
  min-height: 48px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-card);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  text-align: left;
  cursor: pointer;
}
.item small {
  margin-left: 6px;
  font-size: 12px;
  color: var(--color-text-sub);
}
.item.on {
  border: 2px solid var(--color-text);
  background: var(--color-selected-bg);
  font-weight: 700;
}
.ready {
  flex: none;
  padding: 3px 8px;
  border-radius: 10px;
  background: var(--color-safe-bg);
  color: var(--color-green);
  font-size: 11px;
  font-weight: 700;
}
.state {
  margin: 24px 0;
  text-align: center;
  font-size: 14px;
  color: var(--color-text-sub);
}
.foot-text {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--color-text-sub);
}
.apply {
  width: 100%;
  height: 54px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-text);
  color: var(--color-card);
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
</style>
