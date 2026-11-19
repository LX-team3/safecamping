// 지도 종류 (지도 · 스카이뷰) — "지도 설정" 시트에서 고른다
// 탐색 · 내 주변 · 차박 지도가 이 값 하나를 같이 본다 → 한 화면에서 스카이뷰로 바꾸면 다른 지도도 스카이뷰
// 카카오 지도 기본 기능이라 키·비용 추가 없음 (카카오 키가 없는 임시 지도에서는 바뀌는 것이 없다)
// 스카이뷰 = 카카오의 HYBRID (위성사진 위에 도로·지명). 위성사진만 있는 SKYVIEW 는 길 이름이 없어 쓰지 않는다
import { ref } from 'vue'

export const MAP_TYPES = [
  { key: 'roadmap', label: '지도' },
  { key: 'hybrid', label: '스카이뷰' },
]

export const mapType = ref('roadmap')

function typeId(kakao, key) {
  return key === 'hybrid' ? kakao.maps.MapTypeId.HYBRID : kakao.maps.MapTypeId.ROADMAP
}

// 카카오 지도에 지금 고른 종류를 적용한다 (지도를 만든 직후와 mapType 이 바뀔 때 부른다)
export function applyMapType(kakao, map) {
  if (!kakao || !map) return
  map.setMapTypeId(typeId(kakao, mapType.value))
}

// 스카이뷰 미리보기 그림 (카카오 정적 지도 이미지, 인제읍 시내)
// 글자가 안 나오게 위성사진만(SKYVIEW)으로 그린다 — 실제 스카이뷰(HYBRID)에는 도로·지명이 나온다
// (기본 지도 미리보기는 정적 지도에 글자를 끄는 옵션이 없어서 LayerSheet 안의 간단한 그림으로 대신한다)
const PREVIEW_CENTER = { lat: 38.0697, lng: 128.1707 }
export function drawSkyPreview(kakao, el) {
  if (!kakao || !el) return
  el.innerHTML = '' // 다시 그릴 때 이전 그림을 지운다
  new kakao.maps.StaticMap(el, {
    center: new kakao.maps.LatLng(PREVIEW_CENTER.lat, PREVIEW_CENTER.lng),
    level: 4,
    mapTypeId: kakao.maps.MapTypeId.SKYVIEW,
  })
}
