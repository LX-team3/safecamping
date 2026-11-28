// 캠핑 전날 브리핑 (내 캠핑 카드의 초인종 버튼을 누르면 펼쳐지는 줄들)
// 기본은 이미 있는 데이터를 정해진 문장 틀에 넣은 3줄. AI 대비 행동(RAG)을 받으면 2) 진단 줄 자리를 그 줄들로 바꾼다 (아래 buildBriefLines)
// AI 를 못 받으면(실패 · 검사 통과 줄 없음) 이 3줄이 그대로 대체 브리핑이 된다
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

// ---- AI 대비 행동 (POST /api/member/camp-plans/{id}/brief, 서버 brief/) ----
// 서버에 보낼 진단 요약. 웹 진단 결과는 서버 DB 에 칸이 없어 이 브라우저(loadSavedResult)에만 있다 → 요청에 담아 보낸다
// 진단을 안 했으면 null (서버는 일정 · 날씨만으로 만든다)
export function briefSurveyOf(savedSurvey) {
  const A = savedSurvey?.answers
  if (!A) return null
  return { who: A.who || [], kid: A.kid || null, site: A.site || null, weak: savedSurvey.weak || [] }
}

// AI 줄 하나 → 브리핑 줄. 근거는 "행정안전부 2016" 처럼 기관 · 연도만 (제목은 길어서 화면이 좁다)
function aiLine(l, i) {
  const source = l.sources.map((s) => [s.org, s.year].filter(Boolean).join(' ')).join(', ')
  return { key: `ai-${i}`, label: '대비', text: l.text, source }
}

// plan: 내 캠핑 일정, checklist: 출발 준비 응답, savedSurvey: loadSavedResult() 값(없으면 null)
// ai: 서버 브리핑 응답(없거나 ai=false 면 지금의 3줄 그대로)
//   AI 가 있으면 진단 줄 자리에 대비 행동 줄들이 들어간다 (AI 가 진단의 조심할 분야를 반영해 행동을 고르므로)
//   특보 · 날씨 줄과 준비물 줄은 AI 가 아니라 여기서 서버 값으로 만든다 → 숫자는 기상청 값 그대로
export function buildBriefLines(plan, checklist, savedSurvey, checkedIds, ai = null) {
  const middle = ai?.ai && ai.lines?.length ? ai.lines.map(aiLine) : [surveyLine(savedSurvey)]
  return [weatherLine(plan, checklist), ...middle, checklistLine(checklist, checkedIds)]
}
