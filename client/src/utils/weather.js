// 날씨 종류 정하기 (캠핑 기간 날씨 카드의 아이콘 · 바탕색에 씀)
// 서버 forecast.days 의 sky(맑음 · 구름많음 · 흐림) 와 precip(없음 · 비 · 비/눈 · 눈 · 소나기) 를 받아
// 'clear' | 'partly' | 'cloud' | 'rain' | 'snow' 중 하나, 모르면 null 을 돌려준다
// 강수가 있으면 하늘보다 우선한다 (눈이 오는 날 하늘이 "흐림"이어도 눈으로 본다)
export function weatherKind(sky, precip) {
  if (precip && precip !== '없음') {
    if (precip.includes('눈')) return 'snow' // 눈 · 비/눈
    return 'rain' // 비 · 소나기
  }
  if (sky === '맑음') return 'clear'
  if (sky === '구름많음') return 'partly'
  if (sky === '흐림') return 'cloud'
  return null
}
