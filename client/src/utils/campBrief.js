// 캠핑 전날 브리핑 (내 캠핑 카드의 초인종 버튼을 누르면 펼쳐지는 3줄)
// 아직 RAG(문서 검색 + AI 요약)를 붙이기 전이라, 이미 있는 데이터만 정해진 문장 틀에 넣어 만든다
//  1) 특보 · 날씨: 서버 출발 준비 응답(GET /api/member/camp-plans/{id}/checklist)의 warnings · forecast — 기상청 값 그대로
//  2) 진단: 1분 진단 저장 결과(weak = 가장 약한 분야)와 그 분야의 안전 수칙 제목
//  3) 체크리스트: 출발 준비 항목 중 아직 체크하지 않은 개수 + 먼저 챙길 항목 하나
// 수치(기온 · 강수확률)는 지어내지 않는다: 예보를 못 받았으면 "못 받았어요"라고 그대로 쓴다
import { RULES } from '@/utils/webSurvey'

// 출발 준비 체크 상태가 저장된 localStorage 이름 (체크리스트 화면과 같은 이름을 써야 같은 값을 읽는다)
export const checklistStoreKey = (memberId, planId) => `campin.checklist.${memberId ?? 0}.${planId}`

// 체크한 항목 id 목록. 저장소를 못 읽으면(시크릿 창 등) 빈 목록
export function readCheckedIds(memberId, planId) {
  try {
    return JSON.parse(localStorage.getItem(checklistStoreKey(memberId, planId)) || '[]')
  } catch {
    return []
  }
}

// 특보 · 날씨 한 줄
function weatherLine(plan, c) {
  if (c.near === false && c.openDate) {
    const [, m, d] = c.openDate.split('-').map(Number)
    return { key: 'wx', label: '특보·날씨', text: `특보 · 예보는 ${m}월 ${d}일부터 확인할 수 있어요` }
  }
  const warn = c.warnings?.length
    ? `${c.warnings[0].category}${c.warnings.length > 1 ? ` 외 ${c.warnings.length - 1}건` : ''} 발효 중`
    : c.warningsAvailable
      ? '발효 중인 특보 없음'
      : '특보 정보를 받지 못했어요'

  const day = c.forecast?.available ? c.forecast.days?.find((d) => d.date === plan.checkIn) : null
  let wx = '날씨 예보를 받지 못했어요'
  if (day) {
    const parts = []
    if (day.minTemp != null && day.maxTemp != null) parts.push(`${Math.round(day.minTemp)}° ~ ${Math.round(day.maxTemp)}°`)
    if (day.sky) parts.push(day.precip && day.precip !== '없음' ? `${day.sky}(${day.precip})` : day.sky)
    if (day.pop != null) parts.push(`강수 ${day.pop}%`)
    if (parts.length) wx = `출발일 ${parts.join(' · ')}`
  }
  return { key: 'wx', label: '특보·날씨', text: `${warn} · ${wx}`, warn: !!c.warnings?.length }
}

// 진단 한 줄: 저장된 진단이 없으면 진단을 권한다
function surveyLine(saved) {
  const weak = saved?.weak?.[0]
  if (!weak || !RULES[weak]) return { key: 'survey', label: '진단', text: '1분 진단을 하면 나에게 맞는 안전 수칙을 알려 드려요' }
  return { key: 'survey', label: '진단', text: `'${weak}' 대비가 가장 약해요 — ${RULES[weak].title}` }
}

// 체크리스트 한 줄: 특보 · 날씨 대비 항목을 먼저 권한다 (서버가 그 순서로 주므로 첫 번째 미체크 항목)
function checklistLine(c, checkedIds) {
  const items = c.items ?? []
  if (!items.length) return { key: 'check', label: '준비물', text: '출발 준비 항목을 받지 못했어요' }
  const done = new Set(checkedIds)
  const left = items.filter((i) => !done.has(i.id))
  if (!left.length) return { key: 'check', label: '준비물', text: `${items.length}개 모두 챙겼어요` }
  return { key: 'check', label: '준비물', text: `${items.length}개 중 ${left.length}개 남음 · 먼저: ${left[0].text}` }
}

// plan: 내 캠핑 일정, checklist: 출발 준비 응답, savedSurvey: loadSavedResult() 값(없으면 null)
export function buildBriefLines(plan, checklist, savedSurvey, checkedIds) {
  return [weatherLine(plan, checklist), surveyLine(savedSurvey), checklistLine(checklist, checkedIds)]
}
