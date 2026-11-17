// "지도 설정" 시트에서 켠 안전 시설 종류 { er: true, pharmacy: false, ... }
// 화면(ExploreView·ChabakView) 안에 두면 시설 상세로 갔다가 뒤로 오면 화면이 새로 만들어져 다 꺼진다
// → 화면 밖(이 파일)에 두어 앱을 쓰는 동안 유지한다. 탐색·차박 지도가 같이 본다 (지도 종류 utils/mapType.js 와 같은 방식)
// 새로고침하면 처음처럼 전부 꺼짐
import { ref } from 'vue'

export const facilityOn = ref({})

// 서버에서 받은 줄 목록으로 채운다. 이미 켜고 끈 값은 그대로 두고, 처음 보는 종류만 꺼짐으로
export function initFacilityOn(list) {
  facilityOn.value = Object.fromEntries(list.map((l) => [l.code, facilityOn.value[l.code] ?? false]))
}

export function toggleFacilityOn(code) {
  facilityOn.value = { ...facilityOn.value, [code]: !facilityOn.value[code] }
}

// 켜 둔 종류 코드 목록 → API types 값
export const onTypes = () => Object.keys(facilityOn.value).filter((c) => facilityOn.value[c])
