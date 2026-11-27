<script setup>
// 여행 취향 (관광지 탭 또는 내 정보 메뉴에서 들어온다). 고른 취향으로 관광지 순서만 바꾸고 안전점수에는 쓰지 않는다
// 끝나면 ?camp= 로 받은 캠핑장의 관광지 탭으로, 캠핑장 없이 들어왔으면 내 정보로 돌아간다. 1분 진단(survey)과는 별개 화면
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { goUp } from '@/utils/backStack'
import { useTourPreferenceStore } from '@/stores/tourPreference'

const props = defineProps({ campId: { type: Number, default: null } })

const router = useRouter()
const preference = useTourPreferenceStore()

const picked = reactive({ activity: [], nature: [] }) // 저장 버튼을 누르기 전까지는 여기에만 둔다
const loading = ref(true)
const saving = ref(false)
const loadError = ref('')
const error = ref('')

const backTo = computed(() => (props.campId ? `/app/camps/${props.campId}?tab=tour` : '/app/my-info'))

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

function isOn(question, code) {
  return picked[question.code].includes(code)
}

// 최대 개수면 새로 고르지 못하게 막고 안내한다 (고른 것을 빼는 것은 언제나 된다)
function toggle(question, code) {
  error.value = ''
  const current = picked[question.code]
  if (current.includes(code)) {
    picked[question.code] = current.filter((c) => c !== code)
  } else if (current.length >= question.max) {
    error.value = `${question.title} 최대 ${question.max}개까지 고를 수 있어요.`
  } else {
    picked[question.code] = [...current, code]
  }
}

async function save() {
  error.value = ''
  saving.value = true
  try {
    await preference.save({ activity: picked.activity, nature: picked.nature })
    goUp(router, backTo.value)
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="preference">
    <header class="top">
      <span>여행 취향 · 약 30초</span>
      <RouterLink class="later" :to="backTo" @click.capture.prevent="goUp(router, backTo)">나중에 할게요</RouterLink>
    </header>

    <main class="body">
      <h1>캠핑 가서 뭘 하고 싶으세요?</h1>

      <p v-if="loading" class="state">불러오는 중...</p>
      <p v-else-if="loadError" class="state">
        {{ loadError }} <button type="button" class="retry" @click="load">다시 시도</button>
      </p>

      <template v-else>
        <section v-for="q in preference.questions" :key="q.code" class="question">
          <div class="q-head">
            <h2>{{ q.title }}</h2>
            <span>최대 {{ q.max }}개</span>
          </div>
          <div class="pills">
            <button v-for="o in q.options" :key="o.code" type="button" class="pill" :class="{ on: isOn(q, o.code) }"
              :aria-pressed="isOn(q, o.code)" @click="toggle(q, o.code)">
              {{ o.label }}
            </button>
          </div>
        </section>

      </template>
    </main>

    <footer class="foot">
      <p v-if="error" class="warn" role="alert">{{ error }}</p>
      <button type="button" class="save" :disabled="loading || saving || !!loadError" @click="save">
        취향 저장하고 관광지 보기
      </button>
    </footer>
  </div>
</template>

<style scoped>
.preference {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.top {
  flex: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 56px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--color-divider);
  font-size: 14px;
  color: var(--color-text-sub);
}
.later {
  min-height: 44px;
  display: flex;
  align-items: center;
  color: var(--color-text);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 22px 16px 24px;
}
h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 900;
  line-height: 1.3;
  letter-spacing: -0.02em;
}
.state {
  margin: 24px 0 0;
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
.question {
  margin-top: 24px;
}
.q-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}
.q-head span {
  flex: none;
  font-size: 12px;
  color: var(--color-text-sub);
}
.pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.pill {
  min-height: 44px;
  padding: 0 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-card);
  color: var(--color-text);
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}
/* 테두리 두께를 바꾸면 줄이 밀리므로 안쪽 그림자로 굵게 보이게 한다 */
.pill.on {
  border-color: var(--color-text);
  box-shadow: inset 0 0 0 1px var(--color-text);
  background: var(--color-selected-bg);
  font-weight: 700;
}
.foot {
  flex: none;
  padding: 12px 16px 20px;
  border-top: 1px solid var(--color-divider);
}
.warn {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--color-danger);
}
.save {
  width: 100%;
  height: 54px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-card);
  font-family: inherit;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.save:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
