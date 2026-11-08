<script setup>
// 내 정보 화면 (설계: ForYouNew.dc.html 진단 전 / Me.dc.html 진단 후).
// 진단 후 카드에는 유형 이름 대신 고른 답 요약만 보여 준다 (유형 판정 기준이 아직 없음).
// 설계의 "내 유형 안전수칙"·캠핑 기록·안전 체크 카드는 데이터가 없어 아직 뺐다
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSurveyStore } from '@/stores/survey'
import { useMyCampingStore } from '@/stores/myCamping'
import BottomNav from '@/components/common/BottomNav.vue'

const auth = useAuthStore()
const survey = useSurveyStore()
const myCamping = useMyCampingStore()
const router = useRouter()

onMounted(async () => {
  try {
    await survey.fetchQuestions() // 요약에 선택지 이름이 필요하다
    await survey.load(auth.isLoggedIn)
  } catch {
    // 진단 카드를 못 불러와도 나머지 메뉴는 쓸 수 있게 둔다 (진단 전 모양으로 보임)
  }
})

// 아직 화면이 없는 메뉴는 누를 수 없게 두고 "준비 중"으로 안내한다 (href 가 있으면 이동)
// 로그인하면 Me.dc.html 의 메뉴 5개, 안 했으면 ForYouNew.dc.html 의 메뉴 (로그인·알림·공지)
const menu = computed(() =>
  auth.isLoggedIn
    ? [
        { name: '함께 가는 가족', to: '/my-info/family' },
        { name: '여행 취향', to: '/my-info/activity' },
        { name: '비상연락처', to: '/my-info/emergency-contacts' },
        { name: '알림', to: '/my-info/alerts' },
        { name: '공지사항 · 문의' }, // 화면이 아직 없어 누를 수 없음
      ]
    : [
        { name: '로그인 · 회원가입', to: '/login' },
        { name: '알림', to: '/my-info/alerts' },
        { name: '공지사항 · 문의' },
      ],
)

async function logout() {
  await auth.logout()
  survey.reset() // 다음 사람에게 이전 사람의 진단 결과가 보이지 않게
  myCamping.reset() // 내 캠핑 목록도 같은 이유로 비운다
  router.push('/login')
}
</script>

<template>
  <div class="page">
    <div class="me">
      <h1>내 정보</h1>

      <!-- 진단 후 (Me.dc.html) -->
      <div v-if="survey.done && survey.summary" class="diagnosis">
        <div class="head">
          <span class="step">{{ auth.isLoggedIn ? `${auth.member.name}님의 1분 진단` : '내 1분 진단' }}</span>
          <span class="result">{{ survey.summary }}</span>
          <span v-if="!auth.isLoggedIn" class="save-hint">로그인하면 진단 결과가 저장됩니다.</span>
        </div>
        <div class="result-actions">
          <RouterLink to="/survey" class="redo">다시 진단하기</RouterLink>
        </div>
      </div>

      <!-- 진단 전 (ForYouNew.dc.html) -->
      <div v-else class="diagnosis">
        <div class="head">
          <span class="step">3단계 · 5문항 · 약 1분</span>
          <span class="title">아직 내 유형이 없어요<br />1분이면 맞춤으로 바뀌어요</span>
        </div>
        <ol class="points">
          <li><b>1</b><span><b>내 캠핑 유형</b><span>나는 어떤 캠퍼인지 알아봐요</span></span></li>
          <li><b>2</b><span><b>맞춤 안전 수칙</b><span>내 유형에 꼭 필요한 것만</span></span></li>
          <li><b>3</b><span><b>맞춤 캠핑장 순서</b><span>탐색 TOP5가 내 유형 기준으로 바뀌어요</span></span></li>
        </ol>
        <RouterLink to="/survey" class="start">1분 진단 시작하기</RouterLink>
      </div>

      <ul class="menu">
        <li v-for="m in menu" :key="m.name">
          <RouterLink v-if="m.to" :to="m.to" class="row">
            <span class="text"><span class="name">{{ m.name }}</span></span>
            <span class="arrow" aria-hidden="true">›</span>
          </RouterLink>
          <div v-else class="row off">
            <span class="text"><span class="name">{{ m.name }}</span></span>
          </div>
        </li>
        <!-- 설계에 로그아웃 자리가 없어 메뉴 맨 아래에 둠 (위치는 확인 필요) -->
        <li v-if="auth.isLoggedIn">
          <button type="button" class="row" @click="logout">
            <span class="text"><span class="name">로그아웃</span></span>
          </button>
        </li>
      </ul>
    </div>
    <BottomNav active="me" />
  </div>
</template>

<style scoped>
/* 화면 전체 틀: 위는 내용(스크롤), 맨 아래는 하단바 (탐색 화면과 같은 방식) */
.page {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.me {
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
.diagnosis {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 18px;
  border-radius: 18px;
  background: var(--color-text);
  color: var(--color-card);
}
.head {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.step {
  font-size: 12px;
  color: var(--color-text-light);
  letter-spacing: 0.04em;
}
.title {
  font-size: 21px;
  font-weight: 900;
  letter-spacing: -0.02em;
  line-height: 1.35;
}
.points {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.points li {
  display: flex;
  gap: 10px;
  align-items: baseline;
}
.points li > b {
  width: 16px;
  font-size: 13px;
  color: var(--color-accent-light);
}
.points li > span {
  display: flex;
  flex-direction: column;
}
.points li > span > b {
  font-size: 15px;
}
.points li > span > span {
  font-size: 12px;
  color: var(--color-text-light);
}
.start {
  height: 50px;
  border-radius: 25px;
  background: var(--color-card);
  color: var(--color-text);
  font-size: 15px;
  font-weight: 900;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 진단 후 카드 (Me.dc.html) */
.result {
  font-size: 18px;
  font-weight: 900;
  letter-spacing: -0.02em;
  line-height: 1.4;
}
.save-hint {
  font-size: 13px;
  color: var(--color-text-light);
}
.result-actions {
  display: flex;
  gap: 8px;
}
.redo {
  height: 40px;
  padding: 0 16px;
  border-radius: 20px;
  border: 1px solid var(--color-text-sub);
  color: var(--color-card);
  font-size: 14px;
  display: flex;
  align-items: center;
}
.menu {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
}
.row {
  width: 100%;
  display: flex;
  align-items: center;
  height: 60px;
  padding: 0;
  border: 0;
  border-bottom: 1px solid var(--color-line);
  background: transparent;
  text-align: left;
  font-family: inherit;
  color: var(--color-text);
  cursor: pointer;
}
.row.off {
  cursor: default;
}
.text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.name {
  font-size: 16px;
  font-weight: 500;
}
.arrow {
  color: var(--color-arrow);
}
</style>
