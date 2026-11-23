// 예약 정보 확인 화면의 인원 · 동행 · 이동 수단 ↔ 내 캠핑 일정의 memo 글자
// 일정(camp_plan)에 이 칸들이 없어서 memo 한 칸에 글자로 넣는다 (팀 결정: 값을 따로 쓸 기능이 생기면 DB 칸으로 나눔)
// 예: "성인 2명 · 어린이 0명 · 친구·연인 · 자동차"
// 웹(WebBookingCheckView)과 앱(MobileBookingCheckView)이 같이 쓴다 → 글자 모양을 바꾸면 parseBookingMemo 도 같이 고친다

export const WITH = ['혼자', '친구·연인', '아이 동반', '어르신 동반', '반려동물']
export const MOVES = [
  { key: 'car', label: '자동차' }, { key: 'bus', label: '버스' }, { key: 'taxi', label: '택시' },
  { key: 'subway', label: '지하철' }, { key: 'walk', label: '도보' },
]

export function makeBookingMemo({ adults, kids, withs, move }) {
  const people = `성인 ${adults}명 · 어린이 ${kids}명`
  const withText = WITH.filter((w) => withs[w]).join('·')
  const moveLabel = MOVES.find((m) => m.key === move)?.label || ''
  return [people, withText, moveLabel].filter(Boolean).join(' · ')
}

// memo 글자에서 다시 꺼낸다. 이 모양이 아닌 메모(직접 쓴 글)면 찾은 것만 돌려준다 (못 찾은 값은 null → 화면 기본값 유지)
export function parseBookingMemo(memo) {
  const t = memo || ''
  const num = (re) => { const m = t.match(re); return m ? Number(m[1]) : null }
  // '친구·연인' 안에도 '·' 가 있어서 글자를 쪼개지 않고 이름이 들어 있는지로 찾는다
  const found = WITH.filter((w) => t.includes(w))
  return {
    adults: num(/성인 (\d+)명/),
    kids: num(/어린이 (\d+)명/),
    withs: found.length ? Object.fromEntries(found.map((w) => [w, true])) : null,
    move: MOVES.find((m) => t.includes(m.label))?.key || null,
  }
}
