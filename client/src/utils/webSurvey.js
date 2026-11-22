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
const EXP_SHORT = { first: '첫 캠핑', few: '몇 번', often: '자주' }

// 질문 (o: [코드, 글씨, (퀴즈면 점수 | 보조글씨)])
export const PARTS = [
  { chip: 'map', title: '어떤 캠핑을 가나요?', feature: '안전지도', desc: '내 유형에서 안전점수가 높은 캠핑장을 골라 드려요', qs: [
    { k: 'who', t: '누구와 가세요?', h: '여러 개 골라도 돼요', multi: true, solo: 'solo', o: [['solo', '혼자'], ['group', '성인 일행'], ['child', '아이'], ['senior', '어르신', '65세 이상']] },
    { k: 'site', t: '어디서 주무세요?', o: [['glamping', '글램핑·카라반'], ['auto', '오토캠핑 텐트'], ['carcamp', '차박']] },
    { k: 'season', t: '언제 가세요?', o: [['spring', '봄'], ['summer', '여름'], ['fall', '가을'], ['winter', '겨울']] },
    { k: 'exp', t: '캠핑 경험은요?', o: [['first', '처음'], ['few', '몇 번'], ['often', '자주']] },
  ] },
  { chip: 'meta', title: '이럴 땐 어떻게 할까요?', feature: '메타버스', desc: '틀린 것부터 메타버스에서 먼저 배우게 해 드려요', qs: [
    { k: 'q5', quiz: true, ax: '일산화탄소', t: '텐트 안에서 난로를 켜고 잔다면?', o: [['a', '문을 조금 열면 괜찮다', 30], ['b', '끄고 잔다 · 켜면 경보기와 환기', 100], ['c', '약하게 틀면 괜찮다', 0]],
      why: '일산화탄소는 냄새도 색도 없어 잠든 사이 중독돼요. 잠들기 전 끄는 게 가장 안전하고, 켠다면 경보기와 환기가 꼭 함께여야 해요.', v: '일산화탄소 중독 예방' },
    { k: 'q6', quiz: true, ax: '화재', t: '화로에 불씨가 남았다면?', o: [['a', '재만 있으면 그냥 둔다', 0], ['b', '물을 붓고 꺼진 걸 확인한다', 100], ['c', '흙이나 뚜껑으로 덮는다', 40]],
      why: '재 속 불씨는 바람에 다시 살아나요. 물을 붓고 손을 가까이 대 열기가 없는지까지 확인해요.', v: '캠핑 화재 예방' },
    { k: 'q7', quiz: true, ax: '물·호우', t: '계곡물이 갑자기 불어나면?', o: [['a', '텐트에서 지켜본다', 0], ['b', '바로 높은 곳으로 간다', 100], ['c', '얕은 쪽으로 옮긴다', 20]],
      why: '흙탕물과 갑자기 불어나는 물은 상류에 비가 왔다는 신호예요. 짐보다 사람이 먼저, 바로 높은 곳으로 가요.', v: '호우·계곡 고립 대피' },
  ] },
  { chip: 'flood', title: '위급할 때는요?', feature: '침수 예측지도', desc: '내 상황에 맞는 대피 길을 미리 알려 드려요', qs: [
    { k: 'q8', quiz: true, ax: '대피 준비', t: '도착하면 먼저 볼 것은?', o: [['a', '소화기 · 대피로 · 관리동', 100], ['b', '필요할 때 찾는다', 30], ['c', '따로 안 본다', 0]],
      why: '위급할 때는 찾을 시간이 없어요. 도착하자마자 소화기, 대피로, 관리동 위치를 한 번 보고 두세요.', v: '다중이용시설 대피 요령' },
    { k: 'q9', quiz: true, ax: '신고', t: '119에 가장 먼저 말할 것은?', o: [['a', '다친 사람 이름 · 나이', 30], ['b', '캠핑장 이름 · 사이트 번호', 100], ['c', '다친 경위', 30]],
      why: '구조대가 출발하려면 위치가 먼저예요. 캠핑장 이름과 사이트 번호를 미리 기억해 두세요.', v: '119 신고 요령' },
    { k: 'car', t: '위급할 때 차로 이동할 수 있나요?', o: [['yes', '네, 차로'], ['no', '아니요, 걸어서']] },
  ] },
]
export const ALLQ = PARTS.flatMap((p) => p.qs)
const QUIZ = ALLQ.filter((q) => q.quiz)
export const AX = QUIZ.map((q) => q.ax) // 일산화탄소 · 화재 · 물·호우 · 대피 준비 · 신고
export const KID_OPTS = [['baby', '영유아 0~6세'], ['kid', '어린이 7~12세']]
export const emptyAnswers = () => ({ who: [], kid: null, site: null, season: null, exp: null, q5: null, q6: null, q7: null, q8: null, q9: null, car: null })

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

// 유형 = 가장 대비가 약한 분야 (무엇을 조심해야 하는 사람인지)
const TYPES = {
  '물·호우': { lack: '침수 · 호우 대비가', nick: '물가 낙관형', risk: '침수 · 호우 주의', catch: '"비 조금 오는 건 괜찮겠지" 하고 물가에 머무는 편이에요', color: 'water', recTitle: '침수 걱정 적은 캠핑장', recWhy: '하천에서 떨어진 높은 사이트 · 대피로가 가까운 순' },
  '일산화탄소': { lack: '일산화탄소 대비가', nick: '난로 믿음형', risk: '일산화탄소 주의', catch: '"따뜻하면 됐지" 하고 난로를 켠 채 잠들기 쉬워요', color: 'fire', recTitle: '난방 걱정 적은 캠핑장', recWhy: '전기 난방 가능 · 일산화탄소 경보기 대여 순' },
  '화재': { lack: '화재 대비가', nick: '불멍 몰입형', risk: '화재 주의', catch: '불멍에 빠져 마지막 불씨 정리를 깜빡하기 쉬워요', color: 'fire', recTitle: '불 관리가 쉬운 캠핑장', recWhy: '개별 화로대 · 소화기가 가까운 순' },
  '대피 준비': { lack: '대피 준비가', nick: '일단 출발형', risk: '대피 준비 부족', catch: '도착하면 텐트부터! 대피로는 나중에 보는 편이에요', color: 'prep', recTitle: '대피가 쉬운 캠핑장', recWhy: '대피로 표시 · 관리동 24시간 운영 순' },
  '신고': { lack: '신고 요령이', nick: '당황 주의형', risk: '신고 요령 부족', catch: '위급할 때 "여기가 어디지?" 하고 위치부터 막히기 쉬워요', color: 'report', recTitle: '구조가 빠른 캠핑장', recWhy: '사이트 번호 표지 · 119 도착이 빠른 순' },
  safe: { lack: '모든 분야 대비가', nick: '든든 대비형', risk: '지금처럼만', catch: '대부분 안전하게 답했어요! 이대로 떠나도 좋아요', color: 'safe', recTitle: '안전점수 높은 캠핑장', recWhy: '안전점수가 높은 순' },
}
export const RULES = {
  '화재': { emoji: '🔥', title: '불멍은 지정 화로대에서만', body: '캠핑장이 정한 화로대 · 불 피우는 구역만 이용해요. 바람이 센 날은 불을 피우지 않고, 자리를 비우기 전에는 물을 부어 완전히 꺼요.' },
  '일산화탄소': { emoji: '💨', title: '잠들기 전 난로는 끄기', body: '일산화탄소는 냄새도 색도 없어요. 텐트 · 차 안에서는 잠들기 전 난로를 끄고, 꼭 켜야 하면 경보기와 환기를 함께 해요.' },
  '물·호우': { emoji: '🌊', title: '물이 불면 바로 높은 곳으로', body: '흙탕물 · 갑자기 불어나는 물은 상류에 비가 온 신호예요. 짐보다 사람이 먼저, 바로 물가를 벗어나 높은 곳으로 가요.' },
  '대피 준비': { emoji: '🚪', title: '도착하면 대피로부터 확인', body: '위급할 때는 찾을 시간이 없어요. 도착하자마자 소화기 · 대피로 · 관리동 위치를 가족과 함께 봐 두세요.' },
  '신고': { emoji: '📍', title: '내 사이트 번호 외워 두기', body: '구조대가 출발하려면 위치가 먼저예요. 캠핑장 이름과 사이트 번호를 휴대폰에 메모해 두세요.' },
}
const KNOW = [
  { id: 'cpr', text: '응급처치 (심폐소생 · 지혈)', ax: '' },
  { id: 'wx', text: '기상특보 · 낙뢰 · 산사태 대피 요령', ax: '물·호우' },
  { id: 'co2', text: '일산화탄소 중독 증상 (두통 · 어지럼)', ax: '일산화탄소' },
  { id: 'call', text: '119 신고 순서 (위치 → 상황)', ax: '신고' },
]
const CHECKS = [
  { id: 'co', text: '일산화탄소 경보기', ax: '일산화탄소', when: () => true },
  { id: 'ext', text: '소화기 · 대피로 위치 확인', ax: '대피 준비', when: () => true },
  { id: 'site', text: '캠핑장 이름 · 사이트 번호 메모', ax: '신고', when: () => true },
  { id: 'water', text: '물 양동이 (불씨 끄기용)', ax: '화재', when: (A) => A.site !== 'glamping' },
  { id: 'kit', text: '구급상자 · 상비약', ax: '', when: () => true },
  { id: 'light', text: '랜턴 · 여분 배터리', ax: '', when: () => true },
  { id: 'kid', text: '아이 상비약 · 해열제', ax: '', when: (A) => A.who.includes('child') },
  { id: 'warm', text: '방한용품 (침낭 · 핫팩)', ax: '', when: (A) => A.season === 'winter' || A.season === 'fall' },
  { id: 'rain', text: '우비 · 높은 곳 대피 경로 확인', ax: '물·호우', when: (A) => A.season === 'summer' || A.season === 'spring' },
  { id: 'vent', text: '차량 창문 틈 · 환기 확인', ax: '일산화탄소', when: (A) => A.site === 'carcamp' },
]

// 답 → 결과 (설계서와 같은 계산)
export function computeResult(A) {
  const w = PRIORITY.find((p) => A.who.includes(p)) || 'solo'
  const site = SITE[A.site]
  const car = A.site === 'carcamp'
  const sc = {}
  QUIZ.forEach((q) => { sc[q.ax] = q.o.find((x) => x[0] === A[q.k])?.[2] ?? 0 })
  // 상황에 따라 더 조심할 분야에 가중치
  const risk = { '일산화탄소': 0, '화재': 0, '물·호우': 0, '대피 준비': 0, '신고': 0 }
  if (car || A.season === 'winter') risk['일산화탄소'] += 2
  if (A.site === 'auto') risk['화재'] += 1
  if (A.season === 'spring' || A.season === 'fall') risk['화재'] += 1
  if (A.season === 'summer') risk['물·호우'] += 2
  if (A.who.includes('child')) { risk['물·호우'] += 1; risk['신고'] += 1 }
  if (A.who.includes('senior')) risk['신고'] += 1
  if (A.who.includes('solo')) { risk['대피 준비'] += 1; risk['신고'] += 1 }
  if (A.exp === 'first') risk['대피 준비'] += 1
  const byWeak = (a, b) => sc[a] - risk[a] * 5 - (sc[b] - risk[b] * 5)
  const weak = [...AX].sort(byWeak).slice(0, 2)
  const order = [...AX].sort(byWeak).slice(0, 4)

  const total = Math.round(AX.reduce((a, x) => a + sc[x], 0) / AX.length) // 5개 상황 문항 점수 평균
  const safe = total >= 80 && sc[weak[0]] >= 60
  const type = TYPES[safe ? 'safe' : weak[0]]

  const tags = [
    WHO[w].row + (A.kid ? (A.kid === 'baby' ? ' · 영유아' : ' · 어린이') : ''),
    site.col,
    SEASON[A.season],
    EXP[A.exp],
    A.car === 'yes' ? '차량 대피' : '도보 대피',
  ]
  const tagOf = {
    '화재': `방식 · ${site.col}`,
    '일산화탄소': car ? '방식 · 차박' : `계절 · ${SEASON[A.season]}`,
    '물·호우': `계절 · ${SEASON[A.season]}`,
    '대피 준비': `경험 · ${EXP_SHORT[A.exp]}`,
    '신고': `동행 · ${WHO[w].row}`,
  }
  return {
    who: WHO[w].label, siteName: site.name, siteDesc: site.desc, weak, safe, total,
    lackTail: safe ? ' 고르게 높아서' : ` ${sc[weak[0]]}점으로 가장 낮아서`,
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
