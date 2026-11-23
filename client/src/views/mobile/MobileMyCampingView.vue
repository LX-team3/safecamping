<script setup>
// 내 캠핑 탭 첫 화면. 원래 목적은 예약한 캠핑 정보(일정·날씨·위험 알림·재난지도)를 보여 주는 것
// 지금은 캠핑장 상세에서 "내 캠핑에 일정 등록"한 캠핑을 카드로 보여 준다 (서버 저장, stores/myCamping.js)
// 카드 아래 "출발 준비" 줄: 눌러서 체크리스트(ChecklistView)로. 특보는 출발 3일 전부터 서버가 확인해 알려 준다
// 예보(기온·강수) 값은 아직 없다 — 수치를 지어내지 않기 위해 비워 둔다
// 지난 캠핑 카드: 후기 쓰기·수정 (GET /api/member/camp-plans/reviews · POST /api/member/camp-plans/{id}/review, 웹 내 예약과 같은 서버)
// 예약 전·중 카드: "예약 정보 확인" 으로 예약 확인 화면(MobileBookingCheckView)
import { computed, nextTick, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useMyCampingStore } from '@/stores/myCamping'
import { api } from '@/utils/api'
import { fromIso, labelWithDay, nightsBetween, todayIso } from '@/utils/date'
import MobileBottomNav from '@/components/mobile/MobileBottomNav.vue'
import MobileReviewSheet from '@/components/mobile/MobileReviewSheet.vue'
import { METAVERSE_PATH } from '@/utils/webLinks'

// 재난 대피 3D 지도 앱 주소 (router/mobileRoutes.js 의 /disaster 에 /app 이 붙는다). 웹은 /web/disaster
const DISASTER_PATH = '/app/disaster'

const auth = useAuthStore()
const myCamping = useMyCampingStore()

const error = ref('')

async function load() {
  error.value = ''
  if (!auth.isLoggedIn) return
  try {
    await myCamping.load()
  } catch (e) {
    error.value = e.message
    return
  }
  // 끝나지 않은 일정마다 출발 준비 요약(특보·안전점수)을 받는다. 못 받아도 카드는 그대로 (줄에 글자만 빠짐)
  myCamping.plans.filter((p) => p.checkOut >= todayIso()).forEach((p) => myCamping.loadChecklist(p.id).catch(() => {}))
  loadReviews()
  loadFamilyPlans()
}

// ---- 가족 캠핑: 같은 가족방 사람이 등록한 캠핑 (GET /api/family/camp-plans, 보기만) ----
// 가족방이 없으면 서버가 빈 목록을 준다 → 묶음 자체가 안 보인다. 못 받아도 내 캠핑은 그대로
const familyPlans = ref([])
const familyError = ref('')
async function loadFamilyPlans() {
  familyError.value = ''
  try {
    familyPlans.value = await api('/api/family/camp-plans')
  } catch (e) {
    familyError.value = `모임방 캠핑을 불러오지 못했어요. ${e.message}`
  }
}
// 지난 캠핑은 빼고 다가오는·진행 중인 것만 (D-day 표시는 내 카드와 같은 규칙)
const familyCards = computed(() => {
  const today = todayIso()
  return familyPlans.value
    .filter((p) => p.checkOut >= today)
    .map((p) => ({
      ...p,
      badge: p.checkIn <= today ? '캠핑 중' : `D-${nightsBetween(today, p.checkIn)}`,
      range: `${labelWithDay(p.checkIn)} – ${labelWithDay(p.checkOut)} · ${nightsBetween(p.checkIn, p.checkOut)}박`,
    }))
})
onMounted(load)

// ---- 지난 캠핑 후기 ----
// 일정 id → 서버 후기 { planId, content, photos: [{ id, url }], createdAt, updatedAt }
const reviews = ref({})
const reviewError = ref('') // 후기 목록을 못 받았을 때 (카드는 그대로 보인다)
async function loadReviews() {
  reviewError.value = ''
  try {
    const list = await api('/api/member/camp-plans/reviews')
    reviews.value = Object.fromEntries(list.map((r) => [r.planId, r]))
    await nextTick()
    measureAll()
  } catch (e) {
    reviewError.value = `후기를 불러오지 못했어요. ${e.message}`
  }
}

const reviewTarget = ref(null) // 후기 화면을 연 지난 캠핑
const reviewSaving = ref(false)
const reviewSaveError = ref('') // 저장 실패 문구는 후기 화면 안에 보인다 (쓰던 내용이 날아가지 않게 화면은 그대로)
function openReview(p) {
  reviewSaveError.value = ''
  reviewTarget.value = p
}
// 후기 화면이 넘긴 값을 서버 칸 이름 그대로 multipart 로 보낸다 (axios 가 FormData 를 보고 형식을 맞춰 줌)
async function saveReview({ content, keepPhotoIds, files }) {
  const id = reviewTarget.value.id
  const form = new FormData()
  form.append('content', content)
  keepPhotoIds.forEach((pid) => form.append('keepPhotoIds', pid))
  files.forEach((f) => form.append('photos', f))
  reviewSaving.value = true
  reviewSaveError.value = ''
  try {
    const saved = await api(`/api/member/camp-plans/${id}/review`, { method: 'post', body: form })
    reviews.value = { ...reviews.value, [id]: saved }
    expanded.value = { ...expanded.value, [id]: false }
    reviewTarget.value = null
    await nextTick()
    measureAll()
  } catch (e) {
    reviewSaveError.value = e.message
  } finally {
    reviewSaving.value = false
  }
}
// "2026.11.20 작성" (고쳤으면 고친 날짜로 "수정")
function writtenLabel(r) {
  const edited = r.updatedAt && r.createdAt && new Date(r.updatedAt) - new Date(r.createdAt) > 1000
  const d = new Date(edited ? r.updatedAt : r.createdAt)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')} ${edited ? '수정' : '작성'}`
}

// 후기 글은 3줄까지만 보이고, 넘치면 "더보기" (실제로 잘렸는지는 화면에 그린 뒤 높이로 확인)
const expanded = ref({}) // 일정 id → 펼침
const clamped = ref({}) // 일정 id → 3줄을 넘어 잘렸는지
const textEls = {} // 일정 id → 후기 글 요소
function setTextEl(id, el) {
  if (el) textEls[id] = el
  else delete textEls[id]
}
function measureAll() {
  const next = { ...clamped.value }
  Object.entries(textEls).forEach(([id, el]) => {
    if (!expanded.value[id]) next[id] = el.scrollHeight > el.clientHeight + 1
  })
  clamped.value = next
}

const bigPhoto = ref('') // 후기 사진 크게 보기 (아무 데나 누르면 닫힘)

// 출발 준비 줄에 보일 작은 글: 특보 > 특보 없음/받지 못함(가까울 때) > 특보가 보이는 날짜 + 안전점수(먼 일정)
function prepLine(c) {
  if (!c) return { text: '', warn: false } // 아직 못 받음: 줄은 보이되 작은 글자는 비움
  if (c.warnings.length) {
    const more = c.warnings.length > 1 ? ` 외 ${c.warnings.length - 1}건` : ''
    return { text: `${c.warnings[0].category}${more} 발효 중`, warn: true }
  }
  if (c.near) return { text: c.warningsAvailable ? '발효 중인 특보 없음' : '특보 정보를 받지 못했어요', warn: false }
  const d = fromIso(c.openDate)
  const score = c.safety?.score != null ? ` · 안전 ${c.safety.score}` : ''
  return { text: `특보는 ${d.getMonth() + 1}월 ${d.getDate()}일부터${score}`, warn: false }
}

// 카드에 보일 글: 날짜 범위 · 몇 박, 그리고 D-day
const cards = computed(() => {
  const today = todayIso()
  return myCamping.plans.map((p) => {
    let badge
    if (p.checkOut < today) badge = '지난 캠핑'
    else if (p.checkIn <= today) badge = '캠핑 중'
    else badge = `D-${nightsBetween(today, p.checkIn)}`
    const nights = nightsBetween(p.checkIn, p.checkOut)
    return {
      ...p,
      badge,
      past: p.checkOut < today,
      prep: p.checkOut < today ? null : prepLine(myCamping.checklists[p.id]), // 지난 캠핑은 줄 없음
      range: `${labelWithDay(p.checkIn)} – ${labelWithDay(p.checkOut)} · ${nights}박`,
    }
  })
})

async function remove(p) {
  if (!window.confirm(`'${p.campName}' 일정을 지울까요?`)) return
  try {
    await myCamping.remove(p.id)
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div class="page">
    <div class="mycamp">
      <h1>내 캠핑</h1>

      <div v-if="!auth.isLoggedIn" class="empty">
        <p>내 캠핑은 로그인하면 쓸 수 있어요.</p>
        <RouterLink to="/app/login" class="primary">로그인</RouterLink>
      </div>

      <template v-else>
        <!-- 등록한 캠핑 카드. 날씨·위험 알림은 데이터 연결 후 -->
        <p v-if="error" class="placeholder">
          {{ error }} <button type="button" class="retry" @click="load">다시 시도</button>
        </p>
        <ul v-else-if="cards.length" class="plans">
          <li v-for="p in cards" :key="p.id" class="plan" :class="{ past: p.past }">
            <div class="plan-head">
              <span class="tags">
                <span class="badge">{{ p.badge }}</span>
                <span v-if="p.campId == null" class="kind">차박지</span>
              </span>
              <button type="button" class="del" :aria-label="`${p.campName} 일정 지우기`" @click="remove(p)">삭제</button>
            </div>
            <!-- 등록 캠핑장은 캠핑장 상세로, 차박 자리(campId 없음)는 차박 지도에서 그 자리로 -->
            <RouterLink v-if="p.campId != null" :to="`/app/camps/${p.campId}`" class="plan-name">{{ p.campName }}</RouterLink>
            <RouterLink v-else :to="{ path: '/app/chabak', query: { lat: p.lat, lng: p.lng } }" class="plan-name">
              {{ p.campName }}
            </RouterLink>
            <p class="plan-place">{{ p.place }}</p>
            <p class="plan-range">{{ p.range }}</p>
            <p v-if="p.memo" class="plan-memo">{{ p.memo }}</p>
            <RouterLink v-if="p.prep" :to="`/app/my-camping/${p.id}/checklist`" class="prep">
              <span v-if="p.prep.warn" class="warn-dot" aria-label="특보 발효 중" />
              <span class="prep-text">
                <b>출발 준비</b>
                <small v-if="p.prep.text">{{ p.prep.text }}</small>
              </span>
              <span class="arrow" aria-hidden="true">›</span>
            </RouterLink>
            <!-- 예약 사이트에서 예약한 뒤 날짜·인원을 확인해 저장하는 화면 (캠핑장 일정만, 차박 자리는 없음) -->
            <RouterLink v-if="!p.past && p.campId != null" :to="`/app/booking-check?camp=${p.campId}&plan=${p.id}`" class="check-link">
              예약 정보 확인 ›
            </RouterLink>
            <!-- 웹 내 예약 카드와 같은 바로가기: 메타버스 안전 탐험 · 3D 재난 대피 지도 (지난 캠핑에는 없음) -->
            <div v-if="!p.past" class="tools">
              <RouterLink :to="METAVERSE_PATH" class="tool"><span aria-hidden="true">🐢</span>메타버스 안전 탐험</RouterLink>
              <RouterLink :to="DISASTER_PATH" class="tool"><span aria-hidden="true">🌊</span>3D 재난 대피 지도</RouterLink>
            </div>

            <!-- 지난 캠핑: 후기 쓰기 · 수정, 작성한 후기 (사진 → 글 → 작성 날짜) -->
            <template v-if="p.past">
              <p v-if="reviewError" class="review-err">
                {{ reviewError }} <button type="button" class="retry" @click="loadReviews">다시 시도</button>
              </p>
              <button v-else type="button" class="review-btn" :class="{ done: reviews[p.id] }" @click="openReview(p)">
                {{ reviews[p.id] ? '후기 수정하기' : '후기 쓰기' }}
              </button>
              <section v-if="reviews[p.id]" class="myreview" :aria-label="`${p.campName} 내 후기`">
                <div v-if="reviews[p.id].photos.length" class="rphotos">
                  <button v-for="(ph, k) in reviews[p.id].photos" :key="ph.id" type="button" class="rphoto"
                    :aria-label="`후기 사진 ${k + 1} 크게 보기`" @click="bigPhoto = ph.url">
                    <img :src="ph.url" alt="" loading="lazy" />
                  </button>
                </div>
                <p v-if="reviews[p.id].content" :ref="(el) => setTextEl(p.id, el)" class="rtext" :class="{ open: expanded[p.id] }">{{ reviews[p.id].content }}</p>
                <div class="rfoot">
                  <button v-if="clamped[p.id] || expanded[p.id]" type="button" class="more"
                    @click="expanded = { ...expanded, [p.id]: !expanded[p.id] }">{{ expanded[p.id] ? '접기' : '더보기' }}</button>
                  <span class="written">{{ writtenLabel(reviews[p.id]) }}</span>
                </div>
              </section>
            </template>
          </li>
        </ul>
        <p v-else class="placeholder">
          등록한 캠핑이 없어요.<br />캠핑장 상세나 차박 지도에서 '내 캠핑에 일정 등록'을 눌러 보세요.
        </p>

        <!-- 가족 캠핑: 가족방 사람이 등록한 캠핑 (보기만, 삭제·출발 준비 없음) -->
        <section v-if="familyCards.length || familyError" class="family-plans" aria-label="모임방 캠핑">
          <h2>모임방 캠핑</h2>
          <p v-if="familyError" class="placeholder">
            {{ familyError }} <button type="button" class="retry" @click="loadFamilyPlans">다시 시도</button>
          </p>
          <ul v-else class="plans">
            <li v-for="p in familyCards" :key="p.id" class="plan">
              <div class="plan-head">
                <span class="tags">
                  <span class="badge">{{ p.badge }}</span>
                  <span v-if="p.campId == null" class="kind">차박지</span>
                </span>
                <span class="owner">{{ p.ownerName }}님이 등록</span>
              </div>
              <RouterLink v-if="p.campId != null" :to="`/app/camps/${p.campId}`" class="plan-name">{{ p.campName }}</RouterLink>
              <RouterLink v-else :to="{ path: '/app/chabak', query: { lat: p.lat, lng: p.lng } }" class="plan-name">
                {{ p.campName }}
              </RouterLink>
              <p class="plan-place">{{ p.place }}</p>
              <p class="plan-range">{{ p.range }}</p>
              <p v-if="p.memo" class="plan-memo">{{ p.memo }}</p>
              <!-- 가족 일정의 출발 준비도 같은 화면으로 (서버가 같은 가족방이면 보여 준다). 체크 표시는 보는 사람 브라우저에 따로 저장 -->
              <RouterLink :to="`/app/my-camping/${p.id}/checklist`" class="prep">
                <span class="prep-text"><b>출발 준비</b><small>특보 · 날씨 · 준비물 같이 보기</small></span>
                <span class="arrow" aria-hidden="true">›</span>
              </RouterLink>
              <div class="tools">
                <RouterLink :to="METAVERSE_PATH" class="tool"><span aria-hidden="true">🐢</span>메타버스 안전 탐험</RouterLink>
                <RouterLink :to="DISASTER_PATH" class="tool"><span aria-hidden="true">🌊</span>3D 재난 대피 지도</RouterLink>
              </div>
            </li>
          </ul>
        </section>

        <RouterLink to="/app/my-camping/family" class="family-btn">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="9" cy="8" r="3" />
            <circle cx="17" cy="9" r="2.5" />
            <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M15 14.5c.6-.3 1.3-.5 2-.5 2.8 0 4.5 2.2 4.5 5" />
          </svg>
          <span class="text">
            <b>모임방</b>
            <small>함께 가는 사람과 캠핑 일정 · 모일 곳을 같이 봐요</small>
          </span>
          <span aria-hidden="true">›</span>
        </RouterLink>
      </template>
    </div>
    <MobileBottomNav active="mycamp" />

    <MobileReviewSheet :open="!!reviewTarget" :camp-name="reviewTarget?.campName || ''"
      :sub="reviewTarget ? `${labelWithDay(reviewTarget.checkIn)} – ${labelWithDay(reviewTarget.checkOut)}` : ''"
      :review="reviewTarget ? reviews[reviewTarget.id] || null : null"
      :busy="reviewSaving" :error="reviewSaveError"
      @save="saveReview" @cancel="reviewTarget = null" />
    <!-- 후기 사진 크게 보기 -->
    <div v-if="bigPhoto" class="lightbox" role="dialog" aria-modal="true" aria-label="후기 사진 크게 보기" @click="bigPhoto = ''">
      <img :src="bigPhoto" alt="후기 사진" />
    </div>
  </div>
</template>

<style scoped>
/* 화면 전체 틀: 위는 내용(스크롤), 맨 아래는 하단바 (탐색 화면과 같은 방식) */
.page {
  position: relative; /* 후기 화면·사진 크게 보기가 이 화면을 덮는 기준 */
  height: 100%;
  display: flex;
  flex-direction: column;
}
.mycamp {
  flex: 1;
  min-height: 0; /* 이게 없으면 내용이 길 때 하단바가 아래로 밀려난다 */
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px 20px;
}
h1 {
  margin: 0;
  font-size: 32px;
  font-weight: 900;
  letter-spacing: -0.03em;
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin: 48px 0 0;
  font-size: 15px;
  color: var(--color-text-sub);
}
.empty p {
  margin: 0;
}
.primary {
  height: 48px;
  padding: 0 20px;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-card);
  display: inline-flex;
  align-items: center;
  font-size: 15px;
  font-weight: 700;
}
.placeholder {
  margin: 0;
  padding: 24px 16px;
  border-radius: 14px;
  background: var(--color-bg-soft);
  text-align: center;
  font-size: 14px;
  color: var(--color-text-sub);
}
.retry {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  color: var(--color-text);
  text-decoration: underline;
  cursor: pointer;
}
.plans {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.plan {
  padding: 16px;
  border-radius: 14px;
  background: var(--color-bg-soft);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
/* 지난 캠핑은 카드 전체를 흐리게 하면 후기도 흐려져서, 이름·날짜만 연하게 한다 */
.plan.past .plan-name,
.plan.past .plan-range {
  color: var(--color-text-sub);
}
.check-link {
  align-self: flex-start;
  margin-top: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text);
  text-decoration: underline;
}
.family-plans {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.family-plans h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
}
.owner {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-sub);
}
.tools {
  margin-top: 10px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.tool {
  min-height: 44px; /* 손가락으로 누르기 쉬운 높이 */
  padding: 8px 10px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  text-align: center;
}
.review-btn {
  margin-top: 10px;
  height: 44px;
  border: 0;
  border-radius: 22px;
  background: var(--color-text);
  color: var(--color-card);
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
/* 후기를 쓴 뒤에는 테두리 버튼으로 (수정) */
.review-btn.done {
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
}
.review-err {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--color-text-sub);
}
/* 작성한 후기 (카드 안에 펼쳐짐) */
.myreview {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rphotos {
  display: flex;
  gap: 6px;
  overflow-x: auto;
}
.rphoto {
  flex: none;
  width: 72px;
  height: 72px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-photo);
  cursor: pointer;
}
.rphoto img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.rtext {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.rtext.open {
  display: block;
  -webkit-line-clamp: unset;
}
.rfoot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.more {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text);
  cursor: pointer;
}
.written {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-sub);
}
.lightbox {
  position: absolute;
  inset: 0;
  z-index: 110;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.88);
}
.lightbox img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
.plan-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.tags {
  display: flex;
  align-items: center;
  gap: 6px;
}
/* 캠핑장이 아닌 차박 자리 표시 */
.kind {
  padding: 2px 9px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-sub);
}
.badge {
  padding: 3px 10px;
  border-radius: 12px;
  background: var(--color-text);
  color: var(--color-card);
  font-size: 12px;
  font-weight: 700;
}
.del {
  padding: 4px 0 4px 12px;
  border: none;
  background: none;
  font: inherit;
  font-size: 13px;
  color: var(--color-text-sub);
  cursor: pointer;
}
.plan-name {
  font-size: 19px;
  font-weight: 900;
  color: var(--color-text);
}
.plan-place,
.plan-memo {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-sub);
}
.plan-range {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}
.prep {
  margin-top: 12px;
  padding: 11px 12px;
  border-radius: 12px;
  background: var(--color-bg-soft);
  display: flex;
  align-items: center;
  gap: 10px;
}
.prep-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.prep-text b {
  font-size: 14px;
}
.prep-text small {
  font-size: 12px;
  color: var(--color-text-sub);
}
.warn-dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-danger);
}
.prep .arrow {
  font-size: 18px;
  color: var(--color-arrow);
}
.family-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
}
.family-btn .text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.family-btn b {
  font-size: 16px;
}
.family-btn small {
  font-size: 13px;
  color: var(--color-text-sub);
}
</style>
