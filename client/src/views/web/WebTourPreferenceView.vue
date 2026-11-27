<script setup>
// 웹 여행 취향 (앱 views/mobile/MobileTourPreferenceView.vue 와 같은 질문 · 같은 저장소 stores/tourPreference.js)
// 고른 취향으로 관광지 순서만 바꾸고 안전점수에는 쓰지 않는다. 회원이면 서버에, 아니면 이 브라우저 탭에 저장
// 들어오는 곳: 내 정보 "내 활동 › 여행 취향", 캠핑장 상세 관광지 탭 "취향 바꾸기"(?camp=id)
// 끝나면(저장 · 뒤로) ?camp= 가 있으면 그 캠핑장 상세로, 없으면 내 정보로
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WebSubPage from '@/components/web/WebSubPage.vue'
import { useTourPreferenceStore } from '@/stores/tourPreference'

const route = useRoute()
const router = useRouter()
const preference = useTourPreferenceStore()

const picked = reactive({ activity: [], nature: [] }) // 저장 버튼을 누르기 전까지는 여기에만 둔다
const loading = ref(true)
const saving = ref(false)
const loadError = ref('')
const error = ref('')

const campId = computed(() => (/^\d+$/.test(route.query.camp ?? '') ? route.query.camp : null))
const backTo = computed(() => (campId.value ? `/web/camps/${campId.value}` : '/web/my-info'))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    await preference.fetchQuestions()
    await preference.load()
    picked.activity = [...preference.answers.activity]
    picked.nature = [...preference.answers.nature]
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

const isOn = (q, code) => picked[q.code].includes(code)

// 최대 개수면 새로 고르지 못하게 막고 안내한다 (고른 것을 빼는 것은 언제나 된다)
function toggle(q, code) {
  error.value = ''
  const current = picked[q.code]
  if (current.includes(code)) picked[q.code] = current.filter((c) => c !== code)
  else if (current.length >= q.max) error.value = `${q.title} 최대 ${q.max}개까지 고를 수 있어요.`
  else picked[q.code] = [...current, code]
}

async function save() {
  error.value = ''
  saving.value = true
  try {
    await preference.save({ activity: picked.activity, nature: picked.nature })
    router.push(backTo.value)
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <WebSubPage eyebrow="내 활동" title="여행 취향" desc="고른 취향에 맞는 관광지부터 보여 드립니다" narrow>
    <section class="card">
      <h2 class="lead">캠핑 가서 뭘 하고 싶으세요?</h2>

      <p v-if="loading" class="state">불러오는 중...</p>
      <p v-else-if="loadError" class="state">
        {{ loadError }} <button type="button" class="retry" @click="load">다시 시도</button>
      </p>

      <template v-else>
        <div v-for="q in preference.questions" :key="q.code" class="question">
          <div class="q-head">
            <h3>{{ q.title }}</h3>
            <span>최대 {{ q.max }}개</span>
          </div>
          <div class="pills">
            <button v-for="o in q.options" :key="o.code" type="button" class="pill" :class="{ on: isOn(q, o.code) }"
              :aria-pressed="isOn(q, o.code)" @click="toggle(q, o.code)">{{ o.label }}</button>
          </div>
        </div>
      </template>

      <p v-if="error" class="warn" role="alert">{{ error }}</p>
      <div class="btns">
        <RouterLink :to="backTo" class="ghost">뒤로</RouterLink>
        <button type="button" class="primary" :disabled="loading || saving || !!loadError" @click="save">
          취향 저장하기
        </button>
      </div>
    </section>
  </WebSubPage>
</template>

<style scoped>
/* 웹 내 정보(WebMyInfoView)의 카드 · 버튼과 같은 모양 */
.card {
  padding: 30px 32px 28px;
  border: 1px solid var(--web-border-card);
  border-radius: 16px;
  background: var(--web-bg);
}
.lead {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.5px;
}
.state {
  margin: 20px 0 0;
  font-size: 15px;
  color: var(--web-text-sub);
}
.retry {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  color: var(--web-text);
  text-decoration: underline;
  cursor: pointer;
}
.question {
  margin-top: 26px;
}
.q-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}
.q-head h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}
.q-head span {
  font-size: 13px;
  color: var(--web-text-sub);
}
.pills {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
/* 진단(WebSurveyView)의 보기와 같은 모양: 고르면 초록 테두리 + 연초록 바탕 */
.pill {
  padding: 10px 18px;
  border: 1.5px solid var(--web-line-input);
  border-radius: 999px;
  background: var(--web-bg);
  color: var(--web-text);
  font-family: inherit;
  font-size: 15.5px;
  cursor: pointer;
}
.pill:hover {
  border-color: var(--web-green);
}
.pill.on {
  border-color: var(--web-green);
  background: var(--web-best);
  font-weight: 700;
}
.warn {
  margin: 18px 0 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--web-text-warn);
}
.btns {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 30px;
  padding-top: 22px;
  border-top: 1px solid var(--web-line-card);
}
.ghost,
.primary {
  height: 48px;
  padding: 0 24px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}
.ghost {
  border: 1.5px solid var(--web-line-ghost);
  background: var(--web-bg);
  color: var(--web-text);
}
.primary {
  border: 0;
  background: var(--web-green);
  color: var(--web-white);
}
.primary:disabled {
  opacity: 0.45;
  cursor: default;
}
</style>
