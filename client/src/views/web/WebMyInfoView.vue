<script setup>
// 웹 내 정보: 내 계정 · 내 캠핑 유형(1분 진단 저장 결과) · 바로가기 메뉴 · 로그아웃
// 여행 취향은 웹 화면이 아직 없어 "준비 중"으로 둔다 (모바일에서 쓸 수 있다)
import { computed } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import WebSubPage from '@/components/web/WebSubPage.vue'
import { useAuthStore } from '@/stores/auth'
import { useMyCampingStore } from '@/stores/myCamping'
import { useSurveyStore } from '@/stores/survey'
import { loadSavedResult } from '@/utils/webSurvey'
import { DISASTER_PATH, METAVERSE_PATH } from '@/utils/webLinks'

const auth = useAuthStore()
const survey = useSurveyStore()
const myCamping = useMyCampingStore()
const router = useRouter()

const saved = computed(() => (auth.member ? loadSavedResult(auth.member.id) : null))

// to 가 없으면 아직 웹 화면이 없는 항목
const GROUPS = [
  {
    title: '내 활동',
    rows: [
      { name: '내 캠핑', sub: '저장한 캠핑 일정 · 출발 준비', to: '/web/my-booking' },
      { name: '찜한 캠핑장', sub: '비교하고 한눈에 보기', to: '/web/saved' },
      { name: '내 캠핑 유형', sub: '1분 진단 · 다시 하기', to: '/web/survey' },
      { name: '모임방', sub: '일행과 일정 · 모일 곳 공유', to: '/web/family' },
    ],
  },
  {
    title: '안전',
    rows: [
      { name: '알림', sub: '기상특보 · 재난문자', to: '/web/alerts' },
      { name: '메타버스 안전 탐험', sub: '3D 캠핑장에서 안전 퀴즈', to: METAVERSE_PATH },
      { name: '3D 재난 대피 지도', sub: '재난 위험 지역 보기', to: DISASTER_PATH },
    ],
  },
  {
    title: '설정',
    rows: [
      { name: '여행 취향', sub: '준비 중 · 모바일에서 쓸 수 있어요' },
    ],
  },
]

async function logout() {
  await auth.logout()
  survey.reset()
  myCamping.reset()
  router.push('/web')
}
</script>

<template>
  <WebSubPage eyebrow="내 정보" :title="auth.isLoggedIn ? `${auth.member.name}님` : '내 정보'" desc="내 계정과 캠핑 설정을 한곳에서 봅니다" narrow>
    <div v-if="!auth.isLoggedIn" class="card center">
      <p>로그인하면 내 정보와 진단 결과를 볼 수 있어요.</p>
      <div class="btns">
        <RouterLink to="/web/login" class="primary">로그인</RouterLink>
        <RouterLink to="/web/signup" class="ghost">회원가입</RouterLink>
      </div>
    </div>

    <template v-else>
      <section class="card acc">
        <div class="av" aria-hidden="true">{{ auth.member.name.slice(0, 1) }}</div>
        <div class="who">
          <b>{{ auth.member.name }}</b>
          <span>{{ auth.member.loginId }}</span>
        </div>
        <button type="button" class="out" @click="logout">로그아웃</button>
      </section>

      <section class="card type">
        <template v-if="saved">
          <span class="k">내 캠핑 유형</span>
          <b class="nick">{{ saved.who }} · {{ saved.siteName }}</b>
          <span class="weak">보완할 것: {{ saved.weak.join(' · ') }}</span>
          <RouterLink to="/web/survey" class="redo">다시 진단하기</RouterLink>
        </template>
        <template v-else>
          <span class="k">아직 내 유형이 없어요</span>
          <b class="nick">1분이면 맞춤으로 바뀌어요</b>
          <span class="weak">내 캠핑 유형 · 맞춤 안전 수칙 · 맞춤 캠핑장 순서</span>
          <RouterLink to="/web/survey" class="redo">1분 진단 시작하기</RouterLink>
        </template>
      </section>
    </template>

    <section v-for="g in GROUPS" :key="g.title" class="card group">
      <h2>{{ g.title }}</h2>
      <ul>
        <li v-for="r in g.rows" :key="r.name">
          <RouterLink v-if="r.to" :to="r.to" class="row"><span class="n">{{ r.name }}<small>{{ r.sub }}</small></span><i class="arrow"></i></RouterLink>
          <a v-else-if="r.href" :href="r.href" class="row"><span class="n">{{ r.name }}<small>{{ r.sub }}</small></span><i class="arrow"></i></a>
          <div v-else class="row off"><span class="n">{{ r.name }}<small>{{ r.sub }}</small></span></div>
        </li>
      </ul>
    </section>
  </WebSubPage>
</template>

<style scoped>
.card {
  background: var(--web-bg);
  border: 1px solid var(--web-border-card);
  border-radius: 16px;
  padding: 26px 28px;
  margin-bottom: 16px;
}
.card.center {
  text-align: center;
  padding: 60px 26px;
}
.btns {
  display: flex;
  gap: 10px;
  justify-content: center;
}
.primary,
.ghost {
  height: 48px;
  padding: 0 30px;
  border-radius: 999px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
}
.primary {
  background: var(--web-green);
  color: var(--web-white);
}
.ghost {
  border: 1px solid var(--web-line-ghost);
}
.acc {
  display: flex;
  align-items: center;
  gap: 16px;
}
.av {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 22px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}
.who {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.who b {
  font-size: 20px;
}
.who span {
  font-size: 14px;
  color: var(--web-text-sub);
}
.out {
  margin-left: auto;
  height: 40px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid var(--web-line-ghost);
  background: var(--web-bg);
  font-family: inherit;
  font-size: 14px;
  color: var(--web-terracotta);
  cursor: pointer;
}
.type {
  background: var(--web-bg-beige);
  border-color: var(--web-border);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
.k {
  font-size: 13px;
  font-weight: 700;
  color: var(--web-text-sub);
}
.nick {
  font-family: var(--web-font-serif);
  font-size: 24px;
  color: var(--web-green);
}
.weak {
  font-size: 14px;
  color: var(--web-text-sub);
}
.redo {
  margin-top: 10px;
  height: 42px;
  padding: 0 22px;
  border-radius: 999px;
  background: var(--web-green);
  color: var(--web-white);
  font-size: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
}
.group {
  padding: 22px 28px 8px;
}
.group h2 {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 700;
  color: var(--web-text-sub);
}
.group ul {
  margin: 0;
  padding: 0;
  list-style: none;
}
.row {
  padding: 16px 0;
  border-top: 1px solid var(--web-line-card);
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--web-text);
}
.group li:first-child .row {
  border-top: 0;
}
.n {
  font-size: 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.n small {
  font-size: 13px;
  color: var(--web-text-sub);
}
.row.off {
  color: var(--web-text-mute);
}
.arrow {
  width: 8px;
  height: 8px;
  border-top: 2px solid var(--web-arrow);
  border-right: 2px solid var(--web-arrow);
  transform: rotate(45deg);
}
</style>
