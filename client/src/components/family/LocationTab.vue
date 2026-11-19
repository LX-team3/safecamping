<script setup>
// 가족방 · 위치 탭. 위치 공유에 동의한 가족 구성원의 위치를 지도에 보여 준다
// 위치는 서버(family_location 테이블)에 들어 있는 좌표만 읽어 찍는다 (지금은 SQL 로 넣은 시연용 좌표)
//   브라우저 GPS 로 내 위치를 저장하는 기능은 일단 뺐다 (서버 PUT /api/family/locations/me 는 남아 있음)
// 동의 여부: "목록에 내(me) 줄이 있는가"로 판단한다 → 줄이 있으면 묻지 않고 바로 지도
// 탭을 누를 때 이 컴포넌트가 화면에 붙으므로 onMounted 에서 불러오고, 다른 탭으로 가면 onBeforeUnmount 에서 정리한다
// 위치 공유 동의: 동의하기 전에는 지도를 만들지 않는다. 지도 칸은 회색으로 두고 가운데에 "동의하시겠습니까?" 팝업
//   (처음엔 [동의 안 함] [동의합니다], "동의 안 함"을 누르면 안내 문구 + [동의합니다]만)
//   [동의합니다]는 이번 화면에서만 지도를 보여 준다 (서버에 아무것도 저장하지 않음 → 내 줄이 없으면 다음에 또 묻는다)
//   지금 화면에는 공유를 끄는 버튼이 없다 (방을 나가면 서버가 내 위치를 지운다)
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useFamilyStore } from '@/stores/family'
import { fitPoints, keepMapSized, loadKakaoMap } from '@/utils/kakaoMap'

const family = useFamilyStore()

const agreed = ref(null) // null = 서버에 묻는 중, true = 지도 보여 줌(내 줄이 있거나 방금 동의함), false = 동의 전
const asking = ref(false) // 동의 안내창을 [동의 안 함] 버튼까지 띄울지. 동의하지 않았으면 탭에 들어올 때마다 먼저 묻는다
const busy = ref(false) // 위치를 불러오는 중 (버튼 두 번 눌림 방지)
const error = ref('') // 불러오기 실패 문구

onMounted(async () => {
  try {
    await family.fetchLocations()
  } catch (e) {
    error.value = e.message
    return
  }
  agreed.value = family.locations.some((l) => l.me)
  asking.value = !agreed.value
  if (agreed.value) {
    await nextTick() // 지도 칸(v-if)이 화면에 그려진 뒤에 지도를 만든다
    startMap()
  }
})

// 동의합니다: DB 에 저장된 가족 위치를 다시 불러와 지도에 찍는다 (내 위치를 새로 저장하지 않는다)
async function agree() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await family.fetchLocations()
  } catch (e) {
    error.value = e.message
    return
  } finally {
    busy.value = false
  }
  agreed.value = true
  asking.value = false
  await nextTick() // 지도 칸(v-if)이 화면에 그려진 뒤에 지도를 만든다
  startMap()
}

// 동의 안 함: 안내창만 닫는다. 지도는 보여 주지 않고 "동의하면 볼 수 있어요" 안내와 [동의합니다]만 남긴다
function decline() {
  asking.value = false
}

// 처음 보여 줄 지도 중심. 마커를 찍은 뒤 fitPoints 가 마커들이 다 보이게 다시 맞추므로 잠깐만 쓰인다
const DEFAULT_CENTER = { lat: 38.14, lng: 128.205 }

const el = ref(null)
const mode = ref('loading') // loading → kakao | nokey | error
const checkedAt = ref('') // 마지막으로 위치를 불러온 시각 (예: 14:05). 위치가 찍힌 시각이 아니라 "확인한" 시각

let kakao = null
let map = null
let stopSizing = null
let drawn = [] // 지도에 올린 마커 (탭을 떠날 때 지운다)

async function startMap() {
  mode.value = 'loading'
  try {
    kakao = await loadKakaoMap()
  } catch {
    mode.value = 'error'
    return
  }
  if (!el.value) return // 불러오는 사이 다른 탭으로 갔거나 공유를 껐으면 그리지 않는다
  if (!kakao) {
    mode.value = 'nokey' // .env.local 에 카카오 키가 없는 PC
    return
  }
  mode.value = 'kakao'
  map = new kakao.maps.Map(el.value, {
    center: new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
    level: 7,
  })
  // 화면 폭이 바뀌어도(창 크기 조절·휴대폰 회전) 지도가 칸에 맞게 다시 그려지도록 감시한다
  stopSizing = keepMapSized(map, el.value)
  draw() // 위치는 방금(탭 열 때 · 동의할 때) 불러왔으므로 바로 찍는다
}

// 지도와 마커를 치운다 (공유를 끌 때 · 탭을 떠날 때)
function clearMap() {
  stopSizing?.()
  stopSizing = null
  drawn.forEach((o) => o.setMap(null))
  drawn = []
  map = null
  checkedAt.value = ''
}

// 새로고침: DB 의 가족 위치를 다시 불러와 마커를 새로 찍는다 (SQL 로 좌표를 바꿨으면 여기서 반영된다)
// (지도를 손으로 옮겨 가족이 화면 밖으로 나갔을 때도 이 버튼으로 돌아온다)
async function refresh() {
  if (!map || busy.value) return // 지도가 없으면(키 없음·불러오기 실패) 그릴 곳이 없다
  busy.value = true
  error.value = ''
  try {
    await family.fetchLocations()
    draw()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function draw() {
  if (!map) return
  drawn.forEach((o) => o.setMap(null)) // 이전 마커를 지우고 새로 찍는다 (안 지우면 겹쳐 쌓인다)
  const people = family.locations
  // 사람마다 이름표 마커를 올린다. CustomOverlay 는 HTML 요소를 그대로 지도에 띄워서 이름을 같이 보여 줄 수 있다
  drawn = people.map(
    // 기본 기준점(가운데)이 점의 중심이 되도록, 이름표는 CSS 에서 점 아래로 따로 띄운다
    // zIndex: 가족끼리 겹치면 "나"가 위에 보이게 한다
    (p) => new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(p.lat, p.lng), content: pinEl(p), zIndex: p.me ? 5 : 3 }),
  )
  // 패딩 [위, 오른쪽, 아래, 왼쪽]: 점 아래 이름표가 지도 가장자리에 잘리지 않게 아래를 더 띄운다
  // 마지막 3: 두 사람이 가까이 있으면 기본(5)보다 더 확대해야 두 점이 떨어져 보인다
  fitPoints(kakao, map, people, [40, 40, 60, 40], 3)
  checkedAt.value = new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

onBeforeUnmount(clearMap)

// 마커 모양: 파란 점(흰 테두리 + 흰 빛) 아래에 이름표. 이름은 textContent 로 넣는다 (나중에 회원 이름이 들어와도 HTML 로 해석되지 않게)
// 나(로그인한 사람)는 밝은 초록색으로 구분한다
function pinEl(person) {
  const wrap = document.createElement('div')
  wrap.className = person.me ? 'family-pin is-me' : 'family-pin'
  const dot = document.createElement('i')
  dot.className = 'family-pin-dot'
  const label = document.createElement('span')
  label.className = 'family-pin-name'
  label.textContent = person.name
  wrap.append(dot, label)
  return wrap
}
</script>

<template>
  <!-- 안내창이 이 영역 위에 겹쳐 뜨도록 기준 칸(position: relative)으로 감싼다 -->
  <div class="location-tab">
  <!-- 불러오기 · 저장 실패 문구 (모양은 가족방 화면의 .error) -->
  <p v-if="error" class="error" role="alert">{{ error }}</p>

  <!-- 지도 칸은 항상 같은 자리·크기로 보인다. 동의 전에는 회색 칸 + 가운데 안내 팝업, 동의하면 그 칸에 지도 -->
  <div class="map-box" :class="{ locked: !agreed }">
    <!-- 카카오 지도는 이 칸 안에 그려진다. 동의했을 때만 만든다 (공유를 끄면 v-if 로 지도째 치운다) -->
    <div v-if="agreed" ref="el" class="canvas" />
    <template v-if="agreed">
      <p v-if="mode === 'loading'" class="map-msg">지도를 불러오는 중...</p>
      <p v-else-if="mode === 'nokey'" class="map-msg">카카오 지도 키가 없어 지도를 보여 줄 수 없어요.</p>
      <p v-else-if="mode === 'error'" class="map-msg" role="alert">카카오 지도를 불러오지 못했어요.</p>
      <!-- 새로고침: 지도 오른쪽 위에 떠 있는 동그란 버튼. 지도가 떠 있을 때만 보인다 -->
      <button v-if="mode === 'kakao'" type="button" class="map-refresh" aria-label="위치 새로고침" :disabled="busy" @click="refresh">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M20 12a8 8 0 1 1-2.34-5.66" />
          <path d="M20 4v5h-5" />
        </svg>
      </button>
    </template>

    <!-- 동의 전: 회색 지도 칸 가운데에 팝업 (칸 전체를 덮지 않는다)
         처음엔 "동의하시겠습니까?" + [동의 안 함] [동의합니다], "동의 안 함"을 누르면 안내 문구 + [동의합니다]만 남는다 -->
    <!-- 서버에 묻는 중: 안내창을 먼저 띄웠다가 곧바로 지도로 바뀌는 깜빡임을 막는다 -->
    <p v-else-if="agreed === null" class="map-msg">{{ error ? '' : '위치 공유 상태를 확인하는 중...' }}</p>
    <section v-else class="consent-card" :role="asking ? 'dialog' : undefined" aria-labelledby="consent-msg">
      <p v-if="asking" id="consent-msg">내 위치가 이 가족방 구성원에게 보여요.<br />위치 공유에 동의하시겠습니까?</p>
      <p v-else id="consent-msg">위치 공유에 동의하면<br />가족 위치를 볼 수 있어요.</p>
      <div class="consent-actions">
        <button v-if="asking" type="button" class="secondary" :disabled="busy" @click="decline">동의 안 함</button>
        <button type="button" class="primary" :disabled="busy" @click="agree">{{ busy ? '불러오는 중...' : '동의합니다' }}</button>
      </div>
    </section>
  </div>
  </div>
</template>

<style scoped>
/* 탭 내용 전체. 줄 간격은 가족방 화면(.family-room)과 같은 18px */
.location-tab {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
/* 지도 오른쪽 위 새로고침 버튼. 지도(.canvas)가 isolation 으로 가둬져 있어 z-index 1 이면 지도 위에 뜬다 */
.map-refresh {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 1;
  width: 40px;
  height: 40px;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-card);
  color: var(--color-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 6px var(--color-border);
}
/* 동의 전 팝업: 회색 지도 칸 한가운데에 떠 있는 카드. 칸 전체를 덮지 않고 양옆·위아래로 회색이 보이게 폭을 줄인다 */
.consent-card {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 84%;
  padding: 22px 18px 18px;
  border-radius: var(--radius-input);
  background: var(--color-card);
  box-shadow: 0 4px 16px var(--color-border); /* 회색 칸 위에 떠 보이게 */
  text-align: center;
}
.consent-card p {
  margin: 0 0 18px;
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-text-mid);
}
.consent-actions {
  display: flex;
  gap: 8px;
}
.consent-actions button {
  flex: 1;
  padding: 0 12px;
}
.map-box {
  position: relative;
  width: 100%;
  /* 가로는 가족방 화면 폭을 따르고, 세로는 화면 높이의 절반 정도 (너무 작은 화면에서도 280px 은 유지) */
  height: 55vh;
  min-height: 280px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-input);
  overflow: hidden; /* 지도가 둥근 모서리 밖으로 삐져나오지 않게 */
  background: var(--color-map-bg);
}
/* 동의 전: 지도 대신 회색 칸 (사진 자리와 같은 회색) */
.map-box.locked {
  background: var(--color-photo);
}
.canvas {
  width: 100%;
  height: 100%;
  isolation: isolate; /* 지도 안쪽 z-index 가 다른 화면 요소를 덮지 않게 가둔다 */
}
.map-msg {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0 20px;
  text-align: center;
  font-size: 14px;
  color: var(--color-text-sub);
}

/* 마커는 자바스크립트로 만든 요소라 scoped 표시가 붙지 않는다 → :deep 으로 지도 칸 안쪽까지 스타일을 적용한다 */
/* 마커 칸의 크기 = 점 크기 → 카카오가 칸 가운데를 좌표에 맞추므로 점 중심이 정확한 위치가 된다 */
.map-box :deep(.family-pin) {
  position: relative;
}
.map-box :deep(.family-pin-dot) {
  display: block;
  width: 14px;
  height: 14px;
  border: 3px solid var(--color-bg);
  border-radius: 50%;
  background: var(--color-family-pin);
  /* 흰 빛: 지도 위에서 점이 또렷하게 보이도록 테두리 바깥으로 흰색을 번지게 한다 */
  box-shadow: 0 0 8px 3px var(--color-bg);
}
/* 이름표는 점 바로 아래 가운데. 칸 크기에 영향을 주지 않게 absolute 로 띄운다 */
.map-box :deep(.family-pin-name) {
  position: absolute;
  top: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: var(--color-family-pin);
  color: var(--color-bg);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}
/* 나: 점·이름표를 밝은 초록으로 (흰 테두리·흰 빛은 그대로 → 지도 위에서 똑같이 또렷하게) */
.map-box :deep(.family-pin.is-me .family-pin-dot),
.map-box :deep(.family-pin.is-me .family-pin-name) {
  background: var(--color-family-me);
}
</style>
