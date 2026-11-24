// 웹 지도 화면이 같이 쓰는 "지도 위 시설 칩 + 켠 시설" 처리 (탐색 · 저장)
// 칩 목록은 GET /api/nearby/layers, 켠 시설은 GET /api/facilities (지도에 보이는 범위 안)
import { ref } from 'vue'
import { api } from '@/utils/api'
import { initFacilityOn, onTypes, toggleFacilityOn } from '@/utils/facilityLayers'

export function useMapFacilities() {
  const layers = ref([])
  const mapFacilities = ref([])
  let bounds = null
  let reqNo = 0

  async function loadLayers() {
    try {
      const list = await api('/api/nearby/layers')
      layers.value = list.filter((l) => l.ready)
      initFacilityOn(list)
    } catch {
      // 못 불러오면 칩이 안 보일 뿐 화면은 쓸 수 있다
    }
  }

  async function loadFacilities() {
    const no = ++reqNo // 늦게 온 앞 응답이 새 결과를 덮지 않게
    const types = onTypes()
    if (!types.length || !bounds) {
      mapFacilities.value = []
      return
    }
    try {
      const list = await api('/api/facilities', { params: { types: types.join(','), ...bounds } })
      if (no === reqNo) mapFacilities.value = list
    } catch {
      // 못 받으면 시설 없이 캠핑장 핀만 보인다
    }
  }

  function toggleChip(code) {
    toggleFacilityOn(code)
    loadFacilities()
  }
  function onBounds(b) {
    bounds = b
    loadFacilities()
  }
  return { layers, mapFacilities, loadLayers, toggleChip, onBounds }
}
