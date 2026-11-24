// 웹 1분 진단 (PC 화면설계 2-1 · 2-2): 질문 · 분야별 점수 · 유형 6종 · 조심할 것 · 체크리스트
// 설계서의 스크립트를 그대로 옮겼다. 서버(/api/survey)와는 별개로 이 화면 안에서만 계산한다
// (사용자 결정: "설계서 그대로 (유형 6종)", 2026-11-19 — 프로젝트 규칙의 "유형 이름 없이 답 요약만"과 다르니 팀 합의 필요)

export const WHO = {
  child: { label: '아이와 함께하는', row: '아이 동반' },
  senior: { label: '어르신과 함께하는', row: '어르신 동반' },
  group: { label: '일행과 함께하는', row: '성인 일행' },
  solo: { label: '혼자 떠나는', row: '혼자' },
}
const PRIORITY = ['child', 'senior', 'group', 'solo']
const SITE = {
  glamping: { name: '시설을 챙기는 캠퍼', col: '글램핑·카라반', desc: '장비 부담은 적지만, 숙소 안 난방·화기를 조심해야 해요.' },
  auto: { name: '불을 다루는 캠퍼', col: '오토캠핑', desc: '직접 텐트를 치는 만큼 화로·난로를 스스로 관리해야 해요.' },
  carcamp: { name: '환기를 챙기는 캠퍼', col: '차박', desc: '차 안에서 자기 때문에 시동·환기, 그리고 차박이 허용된 곳인지가 중요해요.' },
}
const SEASON = { spring: '봄', summer: '여름', fall: '가을', winter: '겨울' }
const EXP = { first: '첫 캠핑', few: '몇 번 해봄', often: '캠핑 자주' }

// 질문 (o: [코드, 글씨, (퀴즈면 점수 | 보조글씨)])
export const PARTS = [
  { chip: 'map', title: '어떤 캠핑을 가나요?', feature: '', desc: '선택에 따라 추천 캠핑장 점수가 달라집니다', qs: [
    { k: 'who', t: '누구와 가세요?', multi: true, solo: 'solo', o: [['solo', '혼자'], ['group', '성인 일행'], ['child', '아이'], ['senior', '어르신', '65세 이상']] },
    { k: 'site', t: '어디서 주무세요?', o: [['glamping', '글램핑·카라반'], ['auto', '오토캠핑 텐트'], ['carcamp', '차박']] },
    { k: 'season', t: '언제 가세요?', o: [['spring', '봄'], ['summer', '여름'], ['fall', '가을'], ['winter', '겨울']] },
    { k: 'exp', t: '캠핑 경험은요?', o: [['first', '처음'], ['few', '몇 번'], ['often', '자주']] },
    // 이동수단: 예전 "위급할 때는요?"(침수 예측지도) 묶음의 마지막 문항. 그 묶음을 없애면서 이 문항만 여기로 옮겼다
    { k: 'car', t: '위급할 때 차로 이동할 수 있나요?', o: [['yes', '네, 차로'], ['no', '아니요, 걸어서']] },
  ] },
  { chip: 'meta', title: '이럴 땐 어떻게 할까요?', feature: '', desc: '선택에 따라 조심해야 할 것이 달라집니다', qs: [
    { k: 'q5', quiz: true, ax: '일산화탄소', t: '텐트 안에서 난로를 켜고 잔다면?', o: [['a', '문을 조금 열면 괜찮다', 30], ['b', '끄고 잔다 · 켜면 경보기와 환기', 100], ['c', '약하게 틀면 괜찮다', 0]],
      why: '일산화탄소는 냄새도 색도 없어 잠든 사이 중독돼요. 잠들기 전 끄는 게 가장 안전하고, 켠다면 경보기와 환기가 꼭 함께여야 해요.', v: '일산화탄소 중독 예방' },
    { k: 'q6', quiz: true, ax: '화재', t: '화로에 불씨가 남았다면?', o: [['a', '재만 있으면 그냥 둔다', 0], ['b', '물을 붓고 꺼진 걸 확인한다', 100], ['c', '흙이나 뚜껑으로 덮는다', 40]],
      why: '재 속 불씨는 바람에 다시 살아나요. 물을 붓고 손을 가까이 대 열기가 없는지까지 확인해요.', v: '캠핑 화재 예방' },
    { k: 'q7', quiz: true, ax: '물·호우', t: '계곡물이 갑자기 불어나면?', o: [['a', '텐트에서 지켜본다', 0], ['b', '바로 높은 곳으로 간다', 100], ['c', '얕은 쪽으로 옮긴다', 20]],
      why: '흙탕물과 갑자기 불어나는 물은 상류에 비가 왔다는 신호예요. 짐보다 사람이 먼저, 바로 높은 곳으로 가요.', v: '호우·계곡 고립 대피' },
  ] },
  // "위급할 때는요?"(침수 예측지도: 도착 후 먼저 볼 것 · 119 신고) 묶음은 진단에서 뺐다 (2026-11-22 사용자 결정)
]
export const ALLQ = PARTS.flatMap((p) => p.qs)
const QUIZ = ALLQ.filter((q) => q.quiz)
export const AX = QUIZ.map((q) => q.ax) // 일산화탄소 · 화재 · 물·호우
// 가장 어린 아이 나이. 13세 이상(teen)은 고를 수 있지만 결과는 바뀌지 않는다 (성인 일행과 같게 — 팀 결정 2026-11-24)
//   근거: 한국소비자원 캠핑장 사고 통계가 13세 미만을 따로 묶음 (13세 미만이 사고의 61.2%) → 아이 규칙은 12세 이하에만 적용
export const KID_OPTS = [['baby', '영유아 0~6세'], ['kid', '어린이 7~12세'], ['teen', '청소년 13세 이상']]
// 아이 규칙(물·호우 +1 · 아이 체크리스트 · 지도 응급실 가산점)을 적용할지: 아이를 골랐고 12세 이하일 때만
const hasYoungChild = (A) => !!A?.who?.includes('child') && A.kid !== 'teen'
// 해시태그의 "누구와" 칸: 아이는 나이만 (#영유아 · #어린이 · #청소년), 나이를 안 골랐으면 #아이 동반. 나머지는 WHO 의 row
const KID_TAG = { baby: '영유아', kid: '어린이', teen: '청소년' }
const whoTag = (w, A) => (w === 'child' && KID_TAG[A.kid]) || WHO[w].row
export const emptyAnswers = () => ({ who: [], kid: null, site: null, season: null, exp: null, car: null, q5: null, q6: null, q7: null })

// 모든 질문에 답했는지 (아이를 골랐다면 나이대까지)
export function answeredCount(A) {
  return ALLQ.filter((q) => (q.multi ? A[q.k].length > 0 : !!A[q.k])).length
}
export const isReady = (A) => answeredCount(A) === ALLQ.length && (!A.who.includes('child') || !!A.kid)

// 답을 고른다 (복수 선택은 '혼자'와 다른 것을 같이 고를 수 없다)
export function pickAnswer(A, k, val) {
  const next = { ...A, who: [...A.who] }
  if (k === 'kid') next.kid = val
  else {
    const q = ALLQ.find((x) => x.k === k)
    if (q.multi) {
      let v = next[k]
      if (v.includes(val)) v = v.filter((x) => x !== val)
      else if (val === q.solo) v = [val]
      else v = v.filter((x) => x !== q.solo).concat(val)
      next[k] = v
      if (!v.includes('child')) next.kid = null
    } else next[k] = val
  }
  return next
}

// ---- 유형: 캐릭터 6개 (팀 결정 2026-11-24) ----
// 1) 조심할 분야 = "조심할 것" 1위 (퀴즈 점수 − 상황 가중치 × 5, 동점이면 일산화탄소 → 화재 → 물·호우 — 가장 치명적인 것부터)
// 2) 그 분야에 점수를 준 답으로 캐릭터를 고른다 (위가 우선). 상황 답 없이 퀴즈로만 1위가 되면 기본 캐릭터
//    일산화탄소: 차박(계절 무관, 시동·공회전) → 거북이 / 겨울(텐트·글램핑 난로) → 곰 / 기본 거북이 (계절에 안 묶여서)
//    화재:      오토캠핑 → 너구리 / 봄·가을(산불 조심 기간) → 다람쥐 / 기본 너구리
//    물·호우:   아이(12세 이하) → 오리 가족 / 여름(장마·집중호우) → 개구리 / 기본 개구리
// 문구 = 캐릭터 성격(trait) + 지켜야 할 행동 하나(act). 이름은 재밌게, 안전 안내(조심할 것 · 대비할 것)는 그대로 정확하게
// 예전 유형(물가 낙관형 · 난로 믿음형 · 불멍 몰입형 · 든든 대비형)은 없앴다. 퀴즈 정답 여부는 결과에 따로 알리지 않는다 (사용자 결정)
// img: 캐릭터 그림 (Microsoft Fluent Emoji 3D, MIT 라이선스 — client/public/survey/characters/LICENSE-fluentui-emoji.txt)
//   이모지 글자는 기기마다 그림이 달라서(Windows · 아이폰 · 안드로이드) 어디서나 같은 그림 파일로 통일 (사용자 결정 2026-11-24)
//   emoji 는 그림을 못 불러올 때 대신 보여 주는 글자
const IMG = (name) => `/survey/characters/${name}.png`
const CHARACTERS = {
  turtle: { img: IMG('turtle'), emoji: '🐢', nick: '등껍질 거북이', trait: '어디서든 꽁꽁 닫고 들어가 있어야 마음이 편한 타입', act: '차 안에서 잘 땐 시동 끄고 창문 조금 열어 두세요' },
  bear: { img: IMG('bear'), emoji: '🐻', nick: '이불 속 곰', trait: '추운 건 질색이라 일단 따뜻하게 만들고 보는 타입', act: '잠들기 전에 난로는 꼭 끄세요' },
  raccoon: { img: IMG('raccoon'), emoji: '🦝', nick: '불멍 너구리', trait: '불 앞에 앉으면 시간 가는 줄 모르는 타입', act: '자리 뜨기 전에 물 붓고 손 대 봐서 안 뜨거운지 확인하세요' },
  squirrel: { img: IMG('chipmunk'), emoji: '🐿️', nick: '낙엽 다람쥐', trait: '마른 숲, 바스락거리는 낙엽길을 제일 좋아하는 타입', act: '불은 정해진 화로에서만 쓰고, 다 꺼진 불도 다시 한번 보세요' },
  ducks: { img: IMG('duck'), emoji: '🦆', nick: '꼬마 오리 가족', trait: '어딜 가든 아이 챙기느라 바쁜 타입', act: '물가에선 아이가 손 닿는 거리에 있는지 계속 보세요' },
  // 여름 물·호우 가중치의 근거는 장마·집중호우 → 계곡에 가는지는 묻지 않으므로 "비" 캐릭터로 (예전 계곡 수달)
  frog: { img: IMG('frog'), emoji: '🐸', nick: '장마 개구리', trait: '비 오는 날도 캠핑은 못 참는 타입', act: '비가 많이 오면 물가 자리는 피하고, 물이 불어나면 바로 높은 곳으로 가세요' },
}
// 캐릭터를 누르면 말풍선에 차례로 나오는 안전 팁 (분야별). 새로 지어내지 않고 이 파일의 조심할 것(RULES) ·
// 대비할 것(CHECKS · KNOW) 문장에서 가져온 것만 쓴다 — 첫 번째는 캐릭터 자기 행동(act)
const AXIS_TIPS = {
  '일산화탄소': ['일산화탄소는 냄새도 색도 없어요', '난로를 꼭 켜야 하면 일산화탄소 경보기와 환기를 함께 하세요', '두통이나 어지럼이 느껴지면 바로 밖으로 나오세요'],
  '화재': ['캠핑장이 정한 화로대 · 불 피우는 구역만 쓰세요', '바람이 센 날은 불을 피우지 마세요', '불씨 끄기용 물 양동이를 곁에 두세요'],
  '물·호우': ['흙탕물 · 갑자기 불어나는 물은 상류에 비가 온 신호예요', '짐보다 사람이 먼저예요', '떠나기 전에 기상특보를 확인하세요'],
}
// 분야별: 꼬리표 · 색 · 캐릭터 고르는 순서 ([조건, 캐릭터], 마지막은 기본)
const AXIS_TYPES = {
  '일산화탄소': { risk: '일산화탄소 주의', color: 'fire', pick: [[(A) => A.site === 'carcamp', 'turtle'], [(A) => A.season === 'winter', 'bear']], base: 'turtle' },
  '화재': { risk: '화재 주의', color: 'fire', pick: [[(A) => A.site === 'auto', 'raccoon'], [(A) => A.season === 'spring' || A.season === 'fall', 'squirrel']], base: 'raccoon' },
  '물·호우': { risk: '물가 · 호우 주의', color: 'water', pick: [[hasYoungChild, 'ducks'], [(A) => A.season === 'summer', 'frog']], base: 'frog' },
}
function typeOf(A, ax) {
  const t = AXIS_TYPES[ax]
  const key = t.pick.find(([when]) => when(A))?.[1] ?? t.base
  const c = CHARACTERS[key]
  return { key, ax, ...c, risk: t.risk, color: t.color, tips: [c.act, ...AXIS_TIPS[ax]] }
}
export const RULES = {
  '화재': { emoji: '🔥', title: '불멍은 지정 화로대에서만', body: '캠핑장이 정한 화로대 · 불 피우는 구역만 이용해요. 바람이 센 날은 불을 피우지 않고, 자리를 비우기 전에는 물을 부어 완전히 꺼요.' },
  '일산화탄소': { emoji: '💨', title: '잠들기 전 난로는 끄기', body: '일산화탄소는 냄새도 색도 없어요. 텐트 · 차 안에서는 잠들기 전 난로를 끄고, 꼭 켜야 하면 경보기와 환기를 함께 해요.' },
  '물·호우': { emoji: '🌊', title: '물이 불면 바로 높은 곳으로', body: '흙탕물 · 갑자기 불어나는 물은 상류에 비가 온 신호예요. 짐보다 사람이 먼저, 바로 물가를 벗어나 높은 곳으로 가요.' },
}
const KNOW = [
  { id: 'cpr', text: '응급처치 (심폐소생 · 지혈)', ax: '' },
  { id: 'wx', text: '기상특보 · 낙뢰 · 산사태 대피 요령', ax: '물·호우' },
  { id: 'co2', text: '일산화탄소 중독 증상 (두통 · 어지럼)', ax: '일산화탄소' },
  { id: 'call', text: '119 신고 순서 (위치 → 상황)', ax: '' }, // 신고 문항을 뺐으므로 "꼭" 표시는 없다
]
const CHECKS = [
  { id: 'co', text: '일산화탄소 경보기', ax: '일산화탄소', when: () => true },
  { id: 'ext', text: '소화기 · 대피로 위치 확인', ax: '', when: () => true }, // 대피 준비·신고 문항을 뺐으므로 "꼭" 표시는 없다
  { id: 'site', text: '캠핑장 이름 · 사이트 번호 메모', ax: '', when: () => true },
  { id: 'water', text: '물 양동이 (불씨 끄기용)', ax: '화재', when: (A) => A.site !== 'glamping' },
  { id: 'kit', text: '구급상자 · 상비약', ax: '', when: () => true },
  { id: 'light', text: '랜턴 · 여분 배터리', ax: '', when: () => true },
  // ---- 아이 (나이별, 12세 이하만) ----
  // 근거: 소방청 캠핑 안전수칙 "아이가 화기·난방기구 주변에 혼자 가지 않도록 관찰", 한국소비자원 13세 미만 사고 61.2% · 화상 30%
  //       물가 항목은 설계서의 "아이 → 물·호우" 가중치. 숫자 기준(몇 m 등)은 근거가 없어 넣지 않는다
  //  영유아(0~6세): 보호자가 붙어 있어야 하는 나이 → 거리 · 감시 / 어린이(7~12세): 혼자 움직이는 나이 → 약속 · 연락
  { id: 'kid', text: '아이 상비약 · 해열제', ax: '', when: (A) => A.kid === 'kid' && hasYoungChild(A) },
  { id: 'baby-med', text: '아이 상비약 · 해열제 · 체온계', ax: '', when: (A) => A.kid === 'baby' && hasYoungChild(A) },
  { id: 'baby-fire', text: '화로 · 난로 주변에 아이가 혼자 못 가게 거리 두기 (안전망 · 울타리)', ax: '화재', when: (A) => A.kid === 'baby' && hasYoungChild(A) },
  { id: 'baby-water', text: '물가에서는 손 닿는 거리에서 지켜보기', ax: '물·호우', when: (A) => A.kid === 'baby' && hasYoungChild(A) },
  { id: 'kid-fire', text: '화로 · 버너는 만지지 않기로 미리 약속하기', ax: '화재', when: (A) => A.kid === 'kid' && hasYoungChild(A) },
  { id: 'child-vest', text: '아이용 구명조끼 (계곡 · 물가 갈 때)', ax: '물·호우', when: hasYoungChild },
  { id: 'kid-card', text: '이름 · 보호자 연락처 카드, 호루라기 (길 잃었을 때)', ax: '', when: (A) => A.kid === 'kid' && hasYoungChild(A) },
  { id: 'warm', text: '방한용품 (침낭 · 핫팩)', ax: '', when: (A) => A.season === 'winter' || A.season === 'fall' },
  { id: 'rain', text: '우비 · 높은 곳 대피 경로 확인', ax: '물·호우', when: (A) => A.season === 'summer' || A.season === 'spring' },
  { id: 'vent', text: '차량 창문 틈 · 환기 확인', ax: '일산화탄소', when: (A) => A.site === 'carcamp' },
]

// 답을 짧게 요약한 말 목록 (앱 내 정보 카드 · 결과 키워드): "아이 동반 · 영유아", "오토캠핑", "여름", "첫 캠핑", "차량 대피"
export function answerTags(A) {
  const w = PRIORITY.find((p) => A.who.includes(p)) || 'solo'
  return [
    whoTag(w, A),
    SITE[A.site]?.col,
    SEASON[A.season],
    EXP[A.exp],
    A.car === 'yes' ? '차량 대피' : A.car === 'no' ? '도보 대피' : null,
  ].filter(Boolean)
}

// 탐색 화면 추천에 넘기는 캠핑 유형 (서버 camp.camp_type 값: auto · glamping · backpacking)
// 진단의 "어디서 주무세요?" 답을 옮긴다: 글램핑·카라반 → glamping, 오토캠핑 텐트·차박 → auto (앱 예전 선택지도 "오토캠핑장 · 차박 포함"이었다)
// 진단에 노지·백패킹이 없어서 backpacking 은 진단으로 정해지지 않는다. 답이 없으면 '' (유형으로 거르지 않음)
export const campTypeOf = (A) => ({ glamping: 'glamping', auto: 'auto', carcamp: 'auto' })[A?.site] || ''

// ---- 진단 유형 가산점 (지도의 "내 유형 점수") ----
// 아이·어르신·차박·겨울·걸어서 중 하나라도 고르면, 응급실이 가까운 캠핑장일수록 점수를 최대 +5 더해 보여 준다
// 가산점 크기는 서버가 캠핑장마다 계산해 준다 (GET /api/camps 의 erBonus, 기준은 server score/ScoreRules.erBonus)
// 여기서는 "이 사람에게 가산점을 쓸지"만 정한다. 저장된 안전점수 · 등급은 그대로 (화면이 "79 +5" 로 따로 보여 줌)
// 이동 답(걸어서)은 가산점에만 쓰고 조심할 것 · 대비할 것에는 쓰지 않는다 (팀 결정 2026-11-23)
const BONUS_REASONS = [
  [hasYoungChild, '아이 동반'], // 13세 이상(청소년)은 가산점 없음
  [(A) => A.who?.includes('senior'), '어르신 동반'],
  [(A) => A.site === 'carcamp', '차박'],
  [(A) => A.season === 'winter', '겨울'],
  [(A) => A.car === 'no', '걸어서 대피'],
]
// 가산점에 해당하는 내 답 이름들 (없으면 빈 목록 → 가산점 안 씀)
export const erBonusReasons = (A) => (A ? BONUS_REASONS.filter(([f]) => f(A)).map(([, label]) => label) : [])
export const needsErBonus = (A) => erBonusReasons(A).length > 0

// 답 → 결과 (설계서와 같은 계산)
export function computeResult(A) {
  const w = PRIORITY.find((p) => A.who.includes(p)) || 'solo'
  const site = SITE[A.site]
  const car = A.site === 'carcamp'
  const sc = {}
  QUIZ.forEach((q) => { sc[q.ax] = q.o.find((x) => x[0] === A[q.k])?.[2] ?? 0 })
  // 상황에 따라 더 조심할 분야에 가중치
  const risk = { '일산화탄소': 0, '화재': 0, '물·호우': 0 }
  if (car || A.season === 'winter') risk['일산화탄소'] += 2
  if (A.site === 'auto') risk['화재'] += 1
  if (A.season === 'spring' || A.season === 'fall') risk['화재'] += 1
  if (A.season === 'summer') risk['물·호우'] += 2
  if (hasYoungChild(A)) risk['물·호우'] += 1 // 13세 이상(청소년)은 성인과 같게
  const byWeak = (a, b) => sc[a] - risk[a] * 5 - (sc[b] - risk[b] * 5)
  const weak = [...AX].sort(byWeak).slice(0, 2)
  const order = [...AX].sort(byWeak).slice(0, 4)

  const type = typeOf(A, weak[0]) // 조심할 것 1위 분야의 캐릭터

  const tags = [
    whoTag(w, A),
    site.col,
    SEASON[A.season],
    EXP[A.exp],
    A.car === 'yes' ? '차량 대피' : '도보 대피',
  ]
  const tagOf = {
    '화재': `방식 · ${site.col}`,
    '일산화탄소': car ? '방식 · 차박' : `계절 · ${SEASON[A.season]}`,
    '물·호우': `계절 · ${SEASON[A.season]}`,
  }
  return {
    who: WHO[w].label, siteName: site.name, siteDesc: site.desc, weak,
    type, keywords: tags.slice(0, 3).map((t) => `#${t}`),
    bars: AX.map((a) => ({ label: a, v: sc[a], width: Math.max(6, sc[a]) })),
    rules: order.map((ax) => ({ ax, tag: tagOf[ax], ...RULES[ax] })),
    checks: CHECKS.filter((c) => c.when(A)).map((c) => ({ id: c.id, text: c.text, must: weak.includes(c.ax) })),
    know: KNOW.map((c) => ({ id: c.id, text: c.text, must: weak.includes(c.ax) })),
  }
}

// ---- 결과 저장 (로그인한 사람만, 이 브라우저에만) ----
// 서버에 맞는 칸이 없어서 localStorage 에 둔다 → 다른 기기에서는 안 보인다
const SAVE_KEY = 'web-survey-saved'
export function saveResult(memberId, A, r) {
  try {
    const all = JSON.parse(localStorage.getItem(SAVE_KEY)) || {}
    all[memberId] = { answers: A, nick: r.type.nick, who: r.who, siteName: r.siteName, weak: r.weak, savedAt: new Date().toISOString() }
    localStorage.setItem(SAVE_KEY, JSON.stringify(all))
    return true
  } catch {
    return false // 저장소를 못 쓰는 환경
  }
}
export function loadSavedResult(memberId) {
  try {
    return (JSON.parse(localStorage.getItem(SAVE_KEY)) || {})[memberId] || null
  } catch {
    return null
  }
}
