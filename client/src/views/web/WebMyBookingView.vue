<script setup>
// 웹 내 예약 (설계 1-7): 제목 띠 + 탭(다가오는 캠핑 · 지난 캠핑) + 캠핑 한 건마다 카드
// 데이터: 내 캠핑 일정 GET /api/member/camp-plans (캠핑장 상세의 "내 캠핑에 일정 등록"으로 저장),
//         출발 준비 GET /api/member/camp-plans/{id}/checklist (특보 · 예보 · 안전), 캠핑장 GET /api/camps/{id} (사진 · 예약 사이트 · 시설)
// 설계와 다른 점: 인원 · 동행 · 이동 수단 · 체크인 시각은 일정에 저장되는 칸이 없어서 메모로만 보이고, 후기 쓰기는 서버 기능이 없어 준비 중
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/utils/api'
import { fromIso, labelWithDay, nightsBetween, todayIso } from '@/utils/date'
import WebConfirmModal from '@/components/web/WebConfirmModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useMyCampingStore } from '@/stores/myCamping'
import { DISASTER_PATH, METAVERSE_URL } from '@/utils/webLinks'

const auth = useAuthStore()
const myCamping = useMyCampingStore()

const tab = ref('up')
const error = ref('')
const loading = ref(true)
const details = ref({}) // 캠핑장 id → 상세
const open = ref({ 0: true }) // 안전 체크 펼침

async function load() {
  error.value = ''
  if (!auth.isLoggedIn) return (loading.value = false)
  try {
    await myCamping.load()
  } catch (e) {
    error.value = e.message
    loading.value = false
    return
  }
  loading.value = false
  // 끝나지 않은 일정마다 출발 준비를 받는다. 캠핑장이 있으면 상세(사진 · 시설)도. 못 받아도 카드는 그대로 보인다
  myCamping.plans.forEach((p) => {
    if (p.checkOut >= todayIso()) myCamping.loadChecklist(p.id).catch(() => {})
  })
  myCamping.plans.forEach(async (p) => {
    if (p.campId == null || details.value[p.campId]) return
    try {
      details.value = { ...details.value, [p.campId]: await api(`/api/camps/${p.campId}`) }
    } catch {
      // 사진이 없을 뿐이다
    }
  })
}
onMounted(load)

const GRADE_LABEL = { safe: '안심', caution: '보통', danger: '유의' }
const GRADE_CLASS = { safe: 'safe', caution: 'mid', danger: 'warn' }
const today = () => todayIso()
const cards = computed(() =>
  myCamping.plans.map((p) => {
    const past = p.checkOut < today()
    const diff = nightsBetween(today(), p.checkIn)
    const c = myCamping.checklists[p.id]
    const d = details.value[p.campId]
    const minOf = (type) => d?.facilities?.filter((f) => f.type === type).reduce((m, f) => Math.min(m, f.driveMin), Infinity)
    const fin = (v) => (Number.isFinite(v) ? v : null)
    const day0 = c?.forecast?.available ? c.forecast.days?.find((x) => x.minTemp != null || x.maxTemp != null || x.sky) : null
    return {
      ...p,
      past,
      dday: past ? '종료' : p.checkIn <= today() ? '캠핑 중' : `D-${diff}`,
      left: past ? '지난 캠핑' : p.checkIn <= today() ? '지금 캠핑 중이에요' : `${diff}일 뒤 캠핑`,
      nights: nightsBetween(p.checkIn, p.checkOut),
      inDate: labelWithDay(p.checkIn),
      outDate: labelWithDay(p.checkOut),
      image: d?.imageUrl,
      resv: d?.reservationUrl,
      score: c?.safety?.score ?? d?.safetyScore ?? null,
      grade: c?.safety?.grade ?? d?.grade ?? null,
      weather: day0
        ? `${[day0.sky, day0.precip && day0.precip !== '없음' ? day0.precip : ''].filter(Boolean).join(' · ')} ${day0.minTemp ?? '-'}° / ${day0.maxTemp ?? '-'}°`.trim()
        : c?.near === false && c?.openDate ? `${fromIso(c.openDate).getMonth() + 1}월 ${fromIso(c.openDate).getDate()}일부터 보여요` : c ? '예보 없음' : '',
      risk: !c ? '' : c.warnings?.length ? `${c.warnings[0].category}${c.warnings.length > 1 ? ` 외 ${c.warnings.length - 1}건` : ''}` : c.near ? '발효 중인 특보 없음' : c.warningsAvailable === false ? '특보는 출발 3일 전부터' : '발효 중인 특보 없음',
      warn: !!c?.warnings?.length,
      er: c?.safety?.erDriveMin ?? null,
      erApprox: !!c?.safety?.erApprox,
      fire: fin(minOf('fire')),
      shelterNote: '',
    }
  }),
)
const upcoming = computed(() => cards.value.filter((c) => !c.past))
const past = computed(() => cards.value.filter((c) => c.past).reverse())
// 일정 삭제 (캠핑장 등록 일정만 지우는 것이고 캠핑장 정보는 그대로다). 서버가 정본이라 지운 뒤 목록에서 바로 빠진다
const target = ref(null) // 삭제할지 묻는 중인 일정
const removing = ref(false)
const remove = (b) => (target.value = b)
async function confirmRemove() {
  removing.value = true
  error.value = ''
  try {
    await myCamping.remove(target.value.id)
    target.value = null
  } catch (e) {
    error.value = e.message
    target.value = null
  } finally {
    removing.value = false
  }
}
const toggle = (i) => (open.value = { ...open.value, [i]: !open.value[i] })
const hasRisk = (c) => c.risk && !c.risk.startsWith('발효 중인') && !c.risk.startsWith('특보는')
</script>

<template>
  <div class="mybook">
    <!-- 제목 띠 -->
    <section class="banner">
      <img src="/web/banner-night.png" alt="" aria-hidden="true" />
      <span class="shade" aria-hidden="true"></span>
      <div class="inner">
        <h1>내 예약</h1>
        <div class="tabs" role="tablist" aria-label="예약 구분">
          <button type="button" role="tab" :aria-selected="tab === 'up'" :class="{ on: tab === 'up' }" @click="tab = 'up'">다가오는 캠핑 <span>{{ upcoming.length }}</span></button>
          <button type="button" role="tab" :aria-selected="tab === 'past'" :class="{ on: tab === 'past' }" @click="tab = 'past'">지난 캠핑 <span>{{ past.length }}</span></button>
        </div>
      </div>
    </section>

    <main class="main">
      <div class="col">
        <!-- 로그인 전 -->
        <div v-if="!auth.isLoggedIn" class="empty">
          <p>내 예약은 로그인하면 확인할 수 있어요.</p>
          <RouterLink to="/web/login" class="btn">로그인</RouterLink>
        </div>
        <p v-else-if="error" class="state">{{ error }} <button type="button" class="link" @click="load">다시 시도</button></p>
        <p v-else-if="loading" class="state">불러오는 중...</p>

        <!-- 다가오는 캠핑 -->
        <template v-else-if="tab === 'up'">
          <div class="sec">
            <h2>다가오는 캠핑</h2>
            <RouterLink to="/web/explore" class="find">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M5 12h14M12 5v14" /></svg>새 캠핑장 찾기
            </RouterLink>
          </div>
          <p v-if="!upcoming.length" class="state">등록한 캠핑이 없어요. 캠핑장 상세에서 '내 캠핑에 일정 등록'을 눌러 보세요.</p>
          <article v-for="(b, i) in upcoming" :key="b.id" class="card">
            <div class="status">
              <span class="dwrap">
                <span class="dday">{{ b.dday }}</span>
                <span class="dtext"><b>{{ b.left }}</b><span>내 캠핑 일정</span></span>
              </span>
              <span class="acts">
                <RouterLink v-if="b.campId" :to="`/web/booking-check?camp=${b.campId}`" class="edit">예약 정보 확인 ›</RouterLink>
                <button type="button" class="del" :aria-label="`${b.campName} 일정 지우기`" @click="remove(b)">삭제</button>
              </span>
            </div>
            <div class="camp">
              <span class="photo" role="img" :aria-label="`${b.campName} 사진`">
                <img v-if="b.image" :src="b.image" :alt="b.campName" />
                <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2" /><circle cx="8.5" cy="10" r="1.5" /><path d="m21 16-5-5-8 8" /></svg>
              </span>
              <span class="cinfo">
                <RouterLink v-if="b.campId" :to="`/web/camps/${b.campId}`" class="cname">{{ b.campName }}</RouterLink>
                <span v-else class="cname">{{ b.campName }}</span>
                <span class="sub">{{ b.place }}</span>
                <span v-if="b.memo" class="sub">{{ b.memo }}</span>
              </span>
              <span v-if="b.score != null" class="badge" :class="GRADE_CLASS[b.grade] || 'safe'"><span>{{ GRADE_LABEL[b.grade] || '점수' }}</span><b>{{ b.score }}<small>점</small></b></span>
            </div>
            <div class="dates">
              <span><span class="k">체크인</span><b>{{ b.inDate }}</b></span>
              <span class="out"><span class="k">체크아웃 · {{ b.nights }}박</span><b>{{ b.outDate }}</b></span>
            </div>
            <div class="btns">
              <RouterLink v-if="b.campId" :to="`/web/camps/${b.campId}`">캠핑장 상세</RouterLink>
              <span v-else class="off">캠핑장 상세</span>
              <RouterLink to="/web/family">모임방 초대</RouterLink>
              <a v-if="b.resv" :href="b.resv" target="_blank" rel="noopener">예약 사이트 ↗</a>
              <span v-else class="off">예약 사이트 없음</span>
            </div>
            <div class="check">
              <button type="button" class="chead" :aria-expanded="!!open[i]" @click="toggle(i)">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3 4 6v6c0 4.4 3.4 8.2 8 9 4.6-.8 8-4.6 8-9V6z" /><path d="M9 12l2 2 4-4" /></svg>
                <b>캠핑 전 안전 체크</b>
                <span class="csum">{{ b.risk }}</span>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" :style="{ transform: open[i] ? 'rotate(180deg)' : 'none' }"><path d="m6 9 6 6 6-6" /></svg>
              </button>
              <div v-if="open[i]" class="cbody">
                <div class="cells">
                  <span><span class="k">캠핑 날 날씨</span><b>{{ b.weather || '확인 중' }}</b></span>
                  <span><span class="k">주의할 위험</span><b :class="{ warn: hasRisk(b) }">{{ b.risk || '확인 중' }}</b></span>
                  <span><span class="k">가까운 응급실</span><b>{{ b.er != null ? `${b.erApprox ? '약 ' : ''}${b.er}분` : '정보 없음' }}</b></span>
                  <span><span class="k">가까운 소방서</span><b>{{ b.fire != null ? `약 ${b.fire}분` : '정보 없음' }}</b></span>
                </div>
                <RouterLink :to="`/web/checklist/${b.id}`" class="listlink">
                  <span class="lt">안전 점검 체크리스트</span><span class="ls">특보 · 날씨 · 준비물 확인하기 ›</span>
                </RouterLink>
                <div class="tools">
                  <RouterLink :to="b.campId ? `/web/nearby?camp=${b.campId}` : '/web/nearby'" class="main-tool"><span aria-hidden="true">📍</span>주변 안전시설 · 길찾기</RouterLink>
                  <a :href="METAVERSE_URL" target="_blank" rel="noopener" class="sub-tool"><span aria-hidden="true">🐢</span>메타버스 안전 탐험</a>
                  <RouterLink :to="DISASTER_PATH" class="sub-tool"><span aria-hidden="true">🌊</span>3D 재난 대피 지도</RouterLink>
                </div>
              </div>
            </div>
          </article>
        </template>

        <!-- 지난 캠핑 -->
        <template v-else>
          <div class="sec"><h2>지난 캠핑</h2></div>
          <p v-if="!past.length" class="state">지난 캠핑이 아직 없어요.</p>
          <article v-for="p in past" :key="p.id" class="pcard">
            <span class="pphoto" role="img" :aria-label="`${p.campName} 사진`">
              <img v-if="p.image" :src="p.image" :alt="p.campName" />
            </span>
            <div class="pinfo">
              <RouterLink v-if="p.campId" :to="`/web/camps/${p.campId}`" class="pname">{{ p.campName }}</RouterLink>
              <span v-else class="pname">{{ p.campName }}</span>
              <span class="sub">{{ p.inDate }} – {{ p.outDate }} · {{ p.nights }}박<template v-if="p.memo"> · {{ p.memo }}</template></span>
            </div>
            <span class="review" aria-disabled="true">후기 쓰기 (준비 중)</span>
            <button type="button" class="del pdel" :aria-label="`${p.campName} 일정 지우기`" @click="remove(p)">삭제</button>
          </article>
        </template>
      </div>
    </main>
    <WebConfirmModal :open="!!target" title="이 일정을 지울까요?" :text="target ? `'${target.campName}' 일정이 내 예약에서 사라져요.
실제 예약 사이트의 예약은 취소되지 않아요.` : ''"
      confirm-label="삭제" :busy="removing" @confirm="confirmRemove" @cancel="target = null" />
  </div>
</template>

<style scoped>
.banner {
  position: relative;
  background: var(--web-green);
  overflow: hidden;
}
.banner img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 60%;
}
.shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(12, 31, 9, 0.82) 0%, rgba(12, 31, 9, 0.55) 45%, rgba(12, 31, 9, 0.25) 100%);
}
.inner {
  position: relative;
  max-width: 1280px;
  margin: 0 auto;
  padding: 48px 80px 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.inner h1 {
  margin: 0;
  color: var(--web-white);
  font-size: 36px;
  font-weight: 800;
  letter-spacing: -0.8px;
}
.tabs {
  margin-top: 22px;
  display: flex;
  gap: 28px;
}
.tabs button {
  height: 52px;
  padding: 0 2px;
  border: 0;
  border-bottom: 4px solid transparent;
  background: none;
  color: rgba(255, 255, 255, 0.75);
  font-family: inherit;
  font-size: 17px;
  font-weight: 500;
  cursor: pointer;
}
.tabs button span {
  margin-left: 4px;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  color: var(--web-white);
  font-size: 13px;
}
.tabs button.on {
  border-bottom-color: var(--web-white);
  color: var(--web-white);
  font-weight: 800;
}
.tabs button.on span {
  background: var(--web-white);
  color: var(--web-green);
  font-weight: 800;
}
.main {
  background: var(--web-bg-booking);
}
.col {
  max-width: 860px;
  margin: 0 auto;
  padding: 40px 20px 80px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 40px 0;
  color: var(--web-text-sub);
}
.btn {
  height: 48px;
  padding: 0 28px;
  border-radius: 12px;
  background: var(--web-green);
  color: var(--web-white);
  font-weight: 700;
  display: flex;
  align-items: center;
}
.state {
  margin: 0;
  font-size: 15px;
  color: var(--web-text-sub);
}
.link {
  border: 0;
  background: none;
  color: var(--web-link-blue);
  font: inherit;
  cursor: pointer;
  text-decoration: underline;
}
.sec {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.sec h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
}
.find {
  height: 44px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid var(--web-line-panel);
  background: var(--web-bg);
  color: var(--web-text);
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.find svg {
  color: var(--web-green);
}
.card {
  margin-bottom: 4px;
  padding: 22px 24px 24px;
  background: var(--web-bg);
  border: 1px solid var(--web-border-card);
  border-radius: 18px;
  box-shadow: 0 2px 10px rgba(28, 29, 27, 0.05);
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.status {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.dwrap {
  display: flex;
  align-items: center;
  gap: 14px;
}
.dday {
  height: 48px;
  padding: 0 16px;
  border-radius: 14px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 26px;
  font-weight: 900;
  letter-spacing: -0.5px;
  display: flex;
  align-items: center;
}
.dtext {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.dtext b {
  font-size: 17px;
}
.dtext span {
  font-size: 13px;
  font-weight: 600;
  color: var(--web-grade-safe);
}
.acts {
  display: flex;
  align-items: center;
  gap: 14px;
}
.del {
  padding: 0;
  border: 0;
  background: none;
  font-family: inherit;
  font-size: 14px;
  color: var(--web-text-sub);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}
.del:hover {
  color: var(--web-terracotta);
}
.pdel {
  font-size: 14px;
}
.edit {
  font-size: 14px;
  font-weight: 700;
  color: var(--web-green);
  white-space: nowrap;
}
.camp {
  padding-top: 18px;
  border-top: 1px solid var(--web-line-card);
  display: flex;
  align-items: center;
  gap: 16px;
}
.photo {
  flex: 0 0 88px;
  height: 88px;
  border-radius: 12px;
  background: var(--web-photo-empty);
  color: var(--web-text-faint);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.photo img,
.pphoto img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cinfo {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cname {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.4px;
  color: var(--web-text);
}
.sub {
  font-size: 14px;
  color: var(--web-text-sub);
}
.badge {
  flex: 0 0 auto;
  width: 68px;
  height: 68px;
  border-radius: 16px;
  color: var(--web-white);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
}
.badge.safe { background: var(--web-grade-safe); }
.badge.mid { background: var(--web-grade-caution); }
.badge.warn { background: var(--web-grade-danger); }
.badge span {
  font-size: 12px;
  font-weight: 700;
}
.badge b {
  font-size: 24px;
  line-height: 1;
  font-weight: 900;
  letter-spacing: -0.8px;
}
.badge small {
  font-size: 12px;
  font-weight: 800;
}
.dates {
  display: grid;
  grid-template-columns: 1fr 1fr;
}
.dates > span {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.dates .out {
  padding-left: 24px;
  border-left: 1px solid var(--web-border-card);
}
.k {
  font-size: 13px;
  color: var(--web-text-faint);
}
.dates b {
  font-size: 17px;
  font-weight: 600;
}
.btns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.btns > * {
  height: 48px;
  border-radius: 12px;
  background: var(--web-bg-chip);
  color: var(--web-text);
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btns .off {
  color: var(--web-text-faint);
}
.check {
  border-radius: 14px;
  background: var(--web-bg-check);
  overflow: hidden;
}
.chead {
  width: 100%;
  padding: 16px 18px;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  color: var(--web-text);
}
.chead svg:first-child {
  color: var(--web-green);
}
.chead b {
  font-size: 15px;
}
.csum {
  margin-left: auto;
  font-size: 13px;
  color: var(--web-text-sub);
}
.cbody {
  padding: 0 18px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cells {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.cells > span {
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--web-bg);
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.cells .k {
  font-size: 12px;
}
.cells b {
  font-size: 14px;
}
.cells b.warn {
  color: var(--web-grade-danger-fg);
}
.listlink {
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--web-bg);
  color: var(--web-text);
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}
.listlink.dis {
  pointer-events: none;
}
.lt {
  font-weight: 700;
}
.ls {
  margin-left: auto;
  color: var(--web-text-sub);
}
.tools {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.tools > * {
  height: 46px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.tools span[aria-hidden] {
  font-size: 18px;
  line-height: 1;
}
.main-tool {
  background: var(--web-green-deep);
  color: var(--web-white);
}
.sub-tool,
.off-tool {
  background: var(--web-bg);
  border: 1px solid var(--web-border-btn);
  color: var(--web-text);
}
.off-tool {
  color: var(--web-text-faint);
}
.pcard {
  padding: 18px 20px;
  background: var(--web-bg);
  border: 1px solid var(--web-line);
  border-radius: 16px;
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}
.pphoto {
  flex: 0 0 96px;
  height: 72px;
  border-radius: 10px;
  background: var(--web-photo-empty);
  overflow: hidden;
}
.pinfo {
  flex: 999 1 300px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.pname {
  font-size: 18px;
  font-weight: 800;
  color: var(--web-text);
}
.review {
  height: 44px;
  padding: 0 18px;
  border-radius: 10px;
  background: var(--web-bg-chip);
  color: var(--web-text-faint);
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
}
</style>
