// 날짜 도우미. 서버와는 "2026-11-14" 문자열(ISO)로 주고받고, 화면에서만 "11.14 (토)" 처럼 바꾼다
const WEEK = ['일', '월', '화', '수', '목', '금', '토']

// Date → "2026-11-14" (브라우저 시간대 기준. toISOString 은 UTC 라 하루 밀릴 수 있어 쓰지 않는다)
export function toIso(d) {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

// "2026-11-14" → Date (그날 0시)
export function fromIso(iso) {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function todayIso() {
  return toIso(new Date())
}

// "11.14 (토)"
export function labelWithDay(iso) {
  const d = fromIso(iso)
  return `${d.getMonth() + 1}.${d.getDate()} (${WEEK[d.getDay()]})`
}

// "11.14"
export function shortLabel(iso) {
  const d = fromIso(iso)
  return `${d.getMonth() + 1}.${d.getDate()}`
}

// 두 날짜 사이가 며칠(몇 박)인지
export function nightsBetween(a, b) {
  return Math.round((fromIso(b) - fromIso(a)) / 86400000)
}

// 달력 한 달: 앞쪽 빈칸(null) + 1일~말일 ISO 문자열
export function monthCells(year, month) {
  const first = new Date(year, month, 1)
  const last = new Date(year, month + 1, 0).getDate()
  const cells = Array(first.getDay()).fill(null)
  for (let d = 1; d <= last; d++) cells.push(toIso(new Date(year, month, d)))
  return cells
}

export { WEEK }
