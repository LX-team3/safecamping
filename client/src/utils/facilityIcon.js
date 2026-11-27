// 지도 마커에 넣는 시설 종류별 아이콘 (응급실·소방서·경찰서·편의점·약국) + 캠핑장(텐트)
// 출처 (2026-11-17 교체 — 전에는 SVG Repo 에서 모은 제각각 그림이라 스타일·라이선스가 섞여 있었음)
//  - 시설 5개: Lucide (https://lucide.dev, ISC 라이선스 — 상업적 사용 가능, 저작권 표시만 유지) → 선 굵기·모양이 한 세트로 같다
//  - 텐트: Phosphor Icons (https://phosphoricons.com, MIT 라이선스) "tent" fill
// 마커는 카카오 지도 위에 직접 만든 HTML 로 붙어서 Vue 부품을 못 쓴다 → SVG 글을 넣는다
// 검정 대신 currentColor 를 써서 CSS color 로 아이콘 색을 바꾼다 (styles/base.css 의 .fac-badge)
// 크기는 CSS 가 정하도록 width·height 를 지웠다. Lucide 기본 선 굵기 2 → 34px 배지에서 또렷하게 2.25

const ICONS = {
  // 응급실: 십자 (Lucide cross)
  er: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.25" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 9a2 2 0 0 0-2 2v2a2 2 0 0 0 2 2h4a1 1 0 0 1 1 1v4a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2v-4a1 1 0 0 1 1-1h4a2 2 0 0 0 2-2v-2a2 2 0 0 0-2-2h-4a1 1 0 0 1-1-1V4a2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2v4a1 1 0 0 1-1 1z"/></svg>',

  // 소방서: 불꽃 (Lucide flame)
  fire: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.25" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"/></svg>',

  // 경찰서: 방패 (Lucide shield)
  police: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.25" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/></svg>',

  // 편의점: 가게 (Lucide store)
  store: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.25" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m2 7 4.41-4.41A2 2 0 0 1 7.83 2h8.34a2 2 0 0 1 1.42.59L22 7"/><path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/><path d="M15 22v-4a2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2v4"/><path d="M2 7h20"/><path d="M22 7v3a2 2 0 0 1-2 2a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 16 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 12 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 8 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 4 12a2 2 0 0 1-2-2V7"/></svg>',

  // 약국: 알약 (Lucide pill)
  pharmacy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.25" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7Z"/><path d="m8.5 8.5 7 7"/></svg>',

  // 차박 후보지(차박 지도에서 지도를 확대했을 때): 자동차 (Lucide car, 다른 시설 아이콘과 같은 계열·같은 선 굵기)
  car: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.25" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2"/><circle cx="7" cy="17" r="2"/><path d="M9 17h6"/><circle cx="17" cy="17" r="2"/></svg>',

  // 캠핑장(탐색 지도 마커 · 경로 지도 출발지): 채운 텐트 (Phosphor tent fill) — 마커 안 17px 흰 그림이라 채운 모양이 또렷함. 시설은 아니지만 같은 배지 모양을 쓰려고 여기에 둔다
  camp: '<svg viewBox="0 0 256 256" fill="currentColor" aria-hidden="true"><path d="M255.31,188.75l-64-144A8,8,0,0,0,184,40H72a8,8,0,0,0-7.31,4.75h0l0,.12v0L.69,188.75A8,8,0,0,0,8,200H248a8,8,0,0,0,7.31-11.25ZM64,184H20.31L64,85.7Zm16,0V85.7L123.69,184Z"/></svg>',
}

// 안전점수 등급 → 마커 색 이름 (CampService.gradeOf 와 같은 값). 모르는 값은 회색(unknown)
const GRADES = ['safe', 'caution', 'danger']
export const gradeClass = (grade) => (GRADES.includes(grade) ? grade : 'unknown')

// 종류에 맞는 아이콘 SVG 글. 없는 종류(화장실 등)는 빈 글 → 마커에 번호만 보인다
export const facilityIcon = (type) => ICONS[type] || ''

// 동그라미 배지 요소 (+ 오른쪽 위 작은 번호)
//  - 기본: 등급 색 바탕 + 흰 아이콘 (내 주변·경로 지도 — 기준 캠핑장 등급 색)
//  - plain: 흰 바탕 + 진한 아이콘 + 얇은 테두리 (탐색·차박 지도의 "지도 설정" 시설 — 등급 색 캠핑장 마커와 헷갈리지 않게)
// 아이콘은 위 상수에서만 오므로 innerHTML 로 넣어도 안전하다 (사용자 입력이 아님)
export function facilityBadgeEl({ type, grade, no, plain = false }) {
  const badge = document.createElement('span')
  // t-종류: 흰 바탕 배지(plain)의 테두리 · 아이콘 · 바탕을 종류별 색으로 칠할 때 쓴다 (styles/base.css, 웹은 WebCampMap.vue)
  badge.className = `fac-badge ${plain ? 'plain' : gradeClass(grade)} t-${type}`
  badge.innerHTML = facilityIcon(type)
  if (!badge.innerHTML) badge.textContent = no ?? '' // 아이콘이 없으면 번호를 가운데에
  else if (no) {
    const b = document.createElement('b')
    b.className = 'fac-no'
    b.textContent = no
    badge.appendChild(b)
  }
  return badge
}

// 지도 위 시설 배지를 눌렀을 때 위에 뜨는 말풍선: "응급실 · ○○병원  상세 ›" (탐색·차박 지도가 같이 씀)
// 말풍선을 누르면 onOpen (시설 상세 화면으로). 이름은 DB 값이라 textContent 로 넣는다 (innerHTML 금지)
export function facilityPopupEl(f, label, onOpen) {
  const pop = document.createElement('button')
  pop.type = 'button'
  pop.className = 'fac-pop'
  pop.setAttribute('aria-label', `${label} ${f.name} 상세 보기`)
  const type = document.createElement('small')
  type.textContent = label
  const name = document.createElement('b')
  name.textContent = f.name
  const more = document.createElement('span')
  more.textContent = '상세 ›'
  pop.append(type, name, more)
  pop.addEventListener('click', (e) => {
    e.stopPropagation()
    onOpen()
  })
  return pop
}
