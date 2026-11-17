<script setup>
// 가족방 · 위치 탭. 가족 구성원의 위치를 지도에 보여 준다
// 2단계: 더미 가족 위치(가상 인물 2명)를 이름표 마커로 찍는다. 서버·DB 연결은 테이블 협의 후
// 탭을 누를 때 이 컴포넌트가 화면에 붙으므로 onMounted 에서 지도를 만들고, 다른 탭으로 가면 onBeforeUnmount 에서 정리한다
// 위치 공유 동의: 동의하기 전에는 지도를 만들지 않는다. 지도 칸은 회색으로 두고 가운데에 안내 팝업
//   (처음엔 [나중에] [동의하기], "나중에"를 누르면 안내 문구 + [동의하기]만)
//   위쪽 "위치 공유" 스위치로도 켜고 끈다 (켜기 = 동의하기, 끄기 = 동의 철회)
//   시연용이라 동의 여부는 이 브라우저(localStorage)에만 저장한다. 실제 서비스에서는 서버(DB)에 저장해야 한다
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { fitPoints, keepMapSized, loadKakaoMap } from '@/utils/kakaoMap'
import { familyLocationDummy } from '@/data/familyLocationDummy'

const auth = useAuthStore()

// 회원마다 따로 저장한다 (같은 브라우저에서 다른 계정으로 로그인하면 다시 묻도록)
const consentKey = `campin.locationConsent.${auth.member?.id ?? 'guest'}`
const agreed = ref(readConsent())
const asking = ref(!agreed.value) // 동의 안내창을 띄울지. 동의하지 않았으면 탭에 들어올 때마다 먼저 묻는다

// 저장소가 막힌 브라우저(사생활 보호 모드 등)에서도 화면이 멈추지 않게 try 로 감싼다 → 이때는 매번 다시 묻는다
function readConsent() {
  try {
    return localStorage.getItem(consentKey) === 'agreed'
  } catch {
    return false
  }
}
function saveConsent(value) {
  try {
    if (value) localStorage.setItem(consentKey, 'agreed')
    else localStorage.removeItem(consentKey)
  } catch {
    // 저장이 안 돼도 지금 화면에서는 고른 대로 동작한다
  }
}

async function agree() {
  saveConsent(true)
  agreed.value = true
  asking.value = false
  await nextTick() // 지도 칸(v-if)이 화면에 그려진 뒤에 지도를 만든다
  startMap()
}

// 나중에: 안내창만 닫는다. 지도는 보여 주지 않고 "동의하면 볼 수 있어요" 안내를 남긴다
function later() {
  asking.value = false
}

// 공유 끄기: 안내창에서 "언제든 끌 수 있어요"라고 했으므로 끄는 길을 둔다. 지도를 치우고 동의 전 상태로
function withdraw() {
  saveConsent(false)
  clearMap()
  agreed.value = false
}

// 위쪽 위치 공유 스위치: 켜져 있으면 끄고, 꺼져 있으면 켠다
function toggleShare() {
  if (agreed.value) withdraw()
  else agree()
}

// 처음 보여 줄 지도 중심. 마커를 찍은 뒤 fitPoints 가 마커들이 다 보이게 다시 맞추므로 잠깐만 쓰인다
const DEFAULT_CENTER = { lat: 38.14, lng: 128.205 }

const el = ref(null)
const mode = ref('loading') // loading → kakao | nokey | error
const checkedAt = ref('') // 마지막으로 위치를 불러온 시각 (예: 14:05). 위치가 찍힌 시각이 아니라 "확인한" 시각

let people = []
let kakao = null
let map = null
let stopSizing = null
let drawn = [] // 지도에 올린 마커 (탭을 떠날 때 지운다)

onMounted(() => {
  if (agreed.value) startMap() // 이미 동의한 사람만 바로 지도를 띄운다
})

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
  refresh()
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

// 새로고침: 위치를 다시 불러와 마커를 새로 찍고, 가족이 모두 보이게 지도를 다시 맞춘다
// (지도를 손으로 옮겨 가족이 화면 밖으로 나갔을 때도 이 버튼으로 돌아온다)
function refresh() {
  if (!map) return // 지도가 없으면(키 없음·불러오기 실패) 그릴 곳이 없다
  // 지금은 더미라 누를 때마다 같은 값. 나중에 서버 연결 때 await family.fetchLocations() 로 바꾼다
  // 더미의 "나"(me: true) 이름은 지금 로그인한 회원 이름으로 바꿔 보여 준다 → 누가 로그인해도 내 이름이 초록 마커에 나온다
  // (서버 연결 뒤에는 서버가 로그인한 회원을 me 로, 실제 이름과 함께 보내므로 이 처리는 필요 없다)
  people = familyLocationDummy.map((p) => (p.me && auth.member?.name ? { ...p, name: auth.member.name } : p))
  draw()
  checkedAt.value = new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

function draw() {
  drawn.forEach((o) => o.setMap(null)) // 이전 마커를 지우고 새로 찍는다 (안 지우면 겹쳐 쌓인다)
  // 사람마다 이름표 마커를 올린다. CustomOverlay 는 HTML 요소를 그대로 지도에 띄워서 이름을 같이 보여 줄 수 있다
  drawn = people.map(
    // 기본 기준점(가운데)이 점의 중심이 되도록, 이름표는 CSS 에서 점 아래로 따로 띄운다
    // zIndex: 가족끼리 겹치면 "나"가 위에 보이게 한다
    (p) => new kakao.maps.CustomOverlay({ map, position: new kakao.maps.LatLng(p.lat, p.lng), content: pinEl(p), zIndex: p.me ? 5 : 3 }),
  )
  // 패딩 [위, 오른쪽, 아래, 왼쪽]: 점 아래 이름표가 지도 가장자리에 잘리지 않게 아래를 더 띄운다
  // 마지막 3: 두 사람이 군청 안처럼 가까이 있으면 기본(5)보다 더 확대해야 두 점이 떨어져 보인다
  fitPoints(kakao, map, people, [40, 40, 60, 40], 3)
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
  <div class="tab-head">
    <p class="sub">가족 위치 확인</p>
    <!-- 위치 공유 켜기/끄기 스위치. 모양은 내 주변 지도의 "지도에 보일 것" 스위치(LayerSheet)와 같다 -->
    <label class="share-toggle">
      <span>위치 공유</span>
      <button type="button" role="switch" class="switch" :class="{ on: agreed }" :aria-checked="agreed"
        aria-label="위치 공유" @click="toggleShare"><i /></button>
    </label>
  </div>

  <!-- 지도 칸은 항상 같은 자리·크기로 보인다. 동의 전에는 회색 칸 + 가운데 안내 팝업, 동의하면 그 칸에 지도 -->
  <div class="map-box" :class="{ locked: !agreed }">
    <!-- 카카오 지도는 이 칸 안에 그려진다. 동의했을 때만 만든다 (공유를 끄면 v-if 로 지도째 치운다) -->
    <div v-if="agreed" ref="el" class="canvas" />
    <template v-if="agreed">
      <p v-if="mode === 'loading'" class="map-msg">지도를 불러오는 중...</p>
      <p v-else-if="mode === 'nokey'" class="map-msg">카카오 지도 키가 없어 지도를 보여 줄 수 없어요.</p>
      <p v-else-if="mode === 'error'" class="map-msg" role="alert">카카오 지도를 불러오지 못했어요.</p>
      <!-- 새로고침: 지도 오른쪽 위에 떠 있는 동그란 버튼. 지도가 떠 있을 때만 보인다 -->
      <button v-if="mode === 'kakao'" type="button" class="map-refresh" aria-label="위치 새로고침" @click="refresh">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M20 12a8 8 0 1 1-2.34-5.66" />
          <path d="M20 4v5h-5" />
        </svg>
      </button>
    </template>

    <!-- 동의 전: 회색 지도 칸 가운데에 팝업 (칸 전체를 덮지 않는다)
         처음엔 [나중에] [동의하기], "나중에"를 누르면 안내 문구 + [동의하기]만 남는다 -->
    <section v-else class="consent-card" :role="asking ? 'dialog' : undefined" aria-labelledby="consent-msg">
      <p id="consent-msg">내 위치가 이 가족방 구성원에게 보여요.<br />공유는 언제든 끌 수 있어요.</p>
      <div class="consent-actions">
        <button v-if="asking" type="button" class="secondary" @click="later">나중에</button>
        <button type="button" class="primary" @click="agree">동의하기</button>
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
/* 위쪽 "위치 공유 [스위치]" */
.share-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-mid);
}
/* 스위치 모양: components/nearby/LayerSheet.vue 의 .switch 와 같은 값 */
.switch {
  flex: none;
  width: 50px;
  height: 30px;
  padding: 3px;
  border: none;
  border-radius: 15px;
  background: var(--color-track);
  display: flex;
  cursor: pointer;
  transition: background 0.15s;
}
.switch i {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-card);
  transition: transform 0.15s;
}
.switch.on {
  background: var(--color-text);
}
.switch.on i {
  transform: translateX(20px);
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
