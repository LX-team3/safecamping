// 재난 대피 3D 지도 엔진 (CesiumJS)
// disaster-map/app/index.html 의 지도 코드를 Vue 화면에서 쓰도록 옮긴 것.
// - 화면(Vue)은 state(반응형 객체)를 읽어 그리고, 버튼은 아래 actions 를 부른다
// - 지도는 createDisasterMap() 으로 만들고, 화면을 떠날 때 destroy() 로 정리한다 (메모리 누수 방지)
// - 인북천 물빛테마공원 캠핑장 1곳 데이터(public/disaster-map/data.json) 기준
import * as Cesium from 'cesium'
import 'cesium/Build/Cesium/Widgets/widgets.css'

// Cesium 작업 파일(Workers·Assets) 위치 — scripts/copy-cesium.mjs 가 public/cesium 에 복사해 둔다
window.CESIUM_BASE_URL = import.meta.env.BASE_URL + 'cesium/'

// criteria.json 을 못 읽을 때 쓰는 같은 기준표
// 60·90mm 는 기상청 호우특보(주의보·경보) 기준, 120·150mm 는 범람 범위를 보여 주려고 팀이 정한 참고 단계
const DEFAULT_CRIT = {
  max: 200,
  stages: [
    { from: 0, label: '안전', color: '#5FC795', level: 0, flood: 0 },
    { from: 60, label: '주의', color: '#F2C14E', level: 1, flood: 0, alert: '주의보' },
    { from: 90, label: '강가 잠김', color: '#F28C3B', level: 2, flood: 1, alert: '경보' },
    { from: 120, label: '많이 잠김', color: '#EF5B4C', level: 2, flood: 2, ref: true },
    { from: 150, label: '넓게 잠김', color: '#B5172B', level: 3, flood: 3, ref: true },
  ],
}

// 단계별 안내 문구 (level 0~3)
const LV = {
  0: { badge: '안전', color: '#5FC795', head: '지금은 안전합니다', cta: '대피 경로 미리 보기' },
  1: { badge: '주의', color: '#F2C14E', head: '대피소로 이동을 준비하세요', cta: '대피 길안내 시작' },
  2: { badge: '경계', color: '#F28C3B', head: '물이 차기 전에 높은 곳으로 피하세요', cta: '대피 길안내 시작' },
  3: { badge: '심각', color: '#EF5B4C', head: '바로 높은 곳으로 피하세요', cta: '대피 길안내 시작' },
}

// 기상특보: 실제로는 서버가 기상청 특보 API 를 불러 이 모양으로 넘겨 주면 된다. 지금은 화면 확인용 예시
const SAMPLE_ALERT = {
  kind: '호우주의보', action: '발표', level: 'watch',
  area: '강원도 인제군', issuedAt: '2026-11-13T12:00:00+09:00',
  body: '3시간 강우량 60mm 이상 예상', source: '기상청 기상특보 (예시 데이터)',
  rain3h: 60,
}

// 화면이 읽는 상태의 처음 모양
export function initialState() {
  return {
    loading: true, error: '',
    alert: { title: '기상특보 확인 중', time: '', area: '', body: '', src: '', warn: false },
    rain: 60, maxRain: 200, stages: DEFAULT_CRIT.stages,
    stage: { label: '주의', color: '#F2C14E', ai: false },
    lv: { ...LV[1] },
    routeKey: 'base', baseDisabled: false, baseLabel: '대피소', hasBase: true,
    dest: { name: '', meta: '', distNum: '', distUnit: 'km', min: '', sum: '' },
    mode: 'overview',
    nav: { count: '', left: '', no: '', head: '', caution: '', sub: '', bar: 0, prevDisabled: true, nextLabel: '다음' },
    detourBtn: false,
    sheetOpen: false,
    tourOn: false,
    needle: 0,
    todo: ['차보다 걸어서 이동합니다.', '물이 넘친 길과 다리는 건너지 않습니다.', '대피 후 119에 위치를 알립니다.'],
  }
}

/**
 * @param {object} o
 * @param {HTMLElement} o.container  지도가 들어갈 칸
 * @param {HTMLElement} o.tagLayer   지도 위 이름표(HTML)를 붙일 칸
 * @param {HTMLElement} o.credits    지도 출처 표시 칸
 * @param {object} o.state           initialState() 로 만든 reactive 객체
 * @param {string} o.base            데이터 폴더 주소 (예: '/disaster-map/')
 */
export async function createDisasterMap({ container, tagLayer, credits, state, base }) {
  const D = await fetch(base + 'data.json').then(r => {
    if (!r.ok) throw new Error('지도 데이터를 불러오지 못했어요')
    return r.json()
  })
  const CRIT = await fetch(base + 'criteria.json', { cache: 'no-cache' }).then(r => r.json()).catch(() => DEFAULT_CRIT)
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches
  const cleanups = []   // destroy 때 정리할 것들
  let destroyed = false

  /* ============ 지형 (10m DEM + 90m DEM → Cesium 높이맵) ============ */
  function decode(g) {
    const b = atob(g.data), u = new Uint8Array(b.length)
    for (let i = 0; i < b.length; i++) u[i] = b.charCodeAt(i)
    g.arr = new Float32Array(u.buffer); return g
  }
  const fine = decode(D.demFine), coarse = decode(D.demCoarse)
  function bilinear(g, lon, lat, clamp) {
    let fx = (lon - g.west) / g.dx - 0.5, fy = (g.north - lat) / g.dy - 0.5
    if (!clamp && (fx < 0 || fy < 0 || fx > g.w - 1 || fy > g.h - 1)) return null
    fx = Math.min(Math.max(fx, 0), g.w - 1.001); fy = Math.min(Math.max(fy, 0), g.h - 1.001)
    const x0 = Math.floor(fx), y0 = Math.floor(fy), tx = fx - x0, ty = fy - y0, a = g.arr, w = g.w
    const v = [a[y0 * w + x0], a[y0 * w + x0 + 1], a[(y0 + 1) * w + x0], a[(y0 + 1) * w + x0 + 1]]
    if (v.some(z => z < -9000)) { const ok = v.filter(z => z > -9000); return ok.length ? ok.reduce((s, z) => s + z, 0) / ok.length : null }
    return (v[0] * (1 - tx) + v[1] * tx) * (1 - ty) + (v[2] * (1 - tx) + v[3] * tx) * ty
  }
  // 10m 격자 가장자리에서 90m 격자로 자연스럽게 섞이도록 가장자리까지 거리(칸)를 미리 계산
  const BLEND = 30
  ;(function (g) {
    const w = g.w, h = g.h, d = new Float32Array(w * h)
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const i = y * w + x; d[i] = g.arr[i] < -9000 ? 0 : Math.min(x + 1, y + 1, w - x, h - y, BLEND) }
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const i = y * w + x; if (x) d[i] = Math.min(d[i], d[i - 1] + 1); if (y) d[i] = Math.min(d[i], d[i - w] + 1) }
    for (let y = h - 1; y >= 0; y--) for (let x = w - 1; x >= 0; x--) { const i = y * w + x; if (x < w - 1) d[i] = Math.min(d[i], d[i + 1] + 1); if (y < h - 1) d[i] = Math.min(d[i], d[i + w] + 1) }
    g.wt = d
  })(fine)
  function heightAt(lon, lat) {
    const c = bilinear(coarse, lon, lat, true) ?? 0
    const fx = Math.round((lon - fine.west) / fine.dx - 0.5), fy = Math.round((fine.north - lat) / fine.dy - 0.5)
    if (fx < 0 || fy < 0 || fx >= fine.w || fy >= fine.h) return c
    const k = Math.min(fine.wt[fy * fine.w + fx] / BLEND, 1); if (k <= 0) return c
    const f = bilinear(fine, lon, lat, false); return f === null ? c : f * k + c * (1 - k)
  }
  const TS = 33, tiling = new Cesium.GeographicTilingScheme()
  const terrainProvider = new Cesium.CustomHeightmapTerrainProvider({
    width: TS, height: TS, tilingScheme: tiling,
    callback(x, y, level) {
      const r = tiling.tileXYToRectangle(x, y, level), deg = Cesium.Math.toDegrees
      const w = deg(r.west), e = deg(r.east), n = deg(r.north), s = deg(r.south), out = new Float32Array(TS * TS)
      for (let j = 0; j < TS; j++) { const lat = n - (n - s) * j / (TS - 1); for (let i = 0; i < TS; i++) out[j * TS + i] = heightAt(w + (e - w) * i / (TS - 1), lat) }
      return out
    },
  })

  const viewer = new Cesium.Viewer(container, {
    terrainProvider,
    baseLayer: new Cesium.ImageryLayer(new Cesium.OpenStreetMapImageryProvider({ url: 'https://tile.openstreetmap.org/', maximumLevel: 19 })),
    baseLayerPicker: false, geocoder: false, timeline: false, animation: false, homeButton: false, sceneModePicker: false,
    navigationHelpButton: false, fullscreenButton: false, infoBox: false, selectionIndicator: false,
    creditContainer: credits, targetFrameRate: 30, shouldAnimate: false,
  })
  const scene = viewer.scene, camera = viewer.camera
  scene.globe.depthTestAgainstTerrain = true
  scene.globe.maximumScreenSpaceError = 2
  scene.screenSpaceCameraController.minimumZoomDistance = 40
  scene.screenSpaceCameraController.maximumZoomDistance = 30000
  // 손을 떼면 금방 멈추게 (기본 0.9 → 0.5)
  Object.assign(scene.screenSpaceCameraController, { inertiaTranslate: 0.5, inertiaSpin: 0.5, inertiaZoom: 0.5 })
  scene.skyAtmosphere.show = true
  viewer.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
    url: 'https://xdworld.vworld.kr/2d/Satellite/service/{z}/{x}/{y}.jpeg', tilingScheme: new Cesium.WebMercatorTilingScheme(),
    maximumLevel: 18, minimumLevel: 6, rectangle: Cesium.Rectangle.fromDegrees(124, 33, 132, 39), credit: 'VWorld',
  }))

  /* ============ 공통 도우미 ============ */
  const C = (hex, a = 1) => Cesium.Color.fromCssColorString(hex).withAlpha(a)
  const rings = g => g.type === 'Polygon' ? [g.coordinates] : g.type === 'MultiPolygon' ? g.coordinates : []
  const lines = g => g.type === 'LineString' ? [g.coordinates] : g.type === 'MultiLineString' ? g.coordinates : []
  function inRing(p, r) {
    let ins = false
    for (let i = 0, j = r.length - 1; i < r.length; j = i++) {
      const [xi, yi] = r[i], [xj, yj] = r[j]
      if ((yi > p[1]) !== (yj > p[1]) && p[0] < (xj - xi) * (p[1] - yi) / (yj - yi) + xi) ins = !ins
    }
    return ins
  }
  const inFC = (p, fc) => fc.features.some(f => rings(f.geometry).some(q => inRing(p, q[0]) && !q.slice(1).some(h => inRing(p, h))))
  const hier = p => new Cesium.PolygonHierarchy(Cesium.Cartesian3.fromDegreesArray(p[0].flat()), p.slice(1).map(h => new Cesium.PolygonHierarchy(Cesium.Cartesian3.fromDegreesArray(h.flat()))))
  const LAT0 = (D.sat.south + D.sat.north) / 2, MX = 111320 * Math.cos(LAT0 * Math.PI / 180), MY = 110540
  const dist = (a, b) => Math.hypot((a[0] - b[0]) * MX, (a[1] - b[1]) * MY)
  const P3 = (lon, lat, dz = 0) => Cesium.Cartesian3.fromDegrees(lon, lat, heightAt(lon, lat) + dz)
  const headingOf = (a, b) => Math.atan2((b[0] - a[0]) * MX, (b[1] - a[1]) * MY)   // 북쪽 0, 시계방향
  const centroid = r => { let x = 0, y = 0; const n = r.length - 1 || 1; for (let i = 0; i < n; i++) { x += r[i][0]; y += r[i][1] } return [x / n, y / n] }

  // 대피 안내 화살표(›››): 초록 띠 위에 흰 화살표가 목적지 쪽으로 흘러감 + 바깥 흰 테두리(도로·산길 선과 구분)
  const flowMats = []
  function arrowMaterial(hex) {
    const m = new Cesium.Material({ fabric: {
      uniforms: { color: C(hex), arrow: C('#FFFFFF'), repeat: 40, speed: 0.6, time: 0 },
      source: `czm_material czm_getMaterial(czm_materialInput materialInput) {
        czm_material m = czm_getDefaultMaterial(materialInput);
        vec2 st = materialInput.st;
        float v = abs(st.t - 0.5) * 2.0;
        float x = fract(st.s * repeat - time * speed + v * 0.42);
        float band = smoothstep(0.28, 0.36, x) - smoothstep(0.58, 0.66, x);
        band *= 1.0 - smoothstep(0.62, 0.72, v);
        float edge = smoothstep(0.76, 0.82, v);
        m.diffuse = mix(mix(color.rgb, arrow.rgb, band), vec3(1.0), edge);
        m.alpha = 1.0;
        return m;
      }` } })
    flowMats.push(m); return m
  }
  function groundLine(coords, width, appearance) {
    return scene.groundPrimitives.add(new Cesium.GroundPolylinePrimitive({
      geometryInstances: new Cesium.GeometryInstance({ geometry: new Cesium.GroundPolylineGeometry({ positions: Cesium.Cartesian3.fromDegreesArray(coords.flat()), width }) }),
      appearance }))
  }

  /* ============ 차오르는 물 (하천범람지도 50·80·100년 빈도 범위) ============ */
  const WATER = [D.flood50, D.flood80, D.flood100].map(fc => {
    const insts = fc.features.flatMap((ft, i) => rings(ft.geometry).map((p, j) => new Cesium.GeometryInstance({ id: 'w' + i + '_' + j,
      geometry: new Cesium.PolygonGeometry({ polygonHierarchy: hier(p) }),
      attributes: { color: Cesium.ColorGeometryInstanceAttribute.fromColor(C('#2F7FD0', 0)) } })))
    return { alpha: 0, target: 0, prim: insts.length ? scene.groundPrimitives.add(new Cesium.GroundPrimitive({
      geometryInstances: insts, classificationType: Cesium.ClassificationType.TERRAIN, releaseGeometryInstances: false })) : { ready: false, empty: true } }
  })
  function setWaterAlpha(w, a) {
    if (!w.prim.ready) return false
    const c = Cesium.ColorGeometryInstanceAttribute.toValue(C('#2F7FD0', a))
    w.prim.geometryInstances.forEach(gi => { const at = w.prim.getGeometryInstanceAttributes(gi.id); if (at) at.color = c })
    return true
  }

  /* ============ 강 (인북천) — 중심선에 폭을 줘서 물결치는 수면으로 ============ */
  const RIVER_HALF = { river: 18, stream: 5 }          // 평소 강폭의 절반(m)
  const RIVER_SWELL = { 0: 1, 1: 1.35, 2: 1.7, 3: 2.1 } // 비가 올수록 넓어짐
  let riverPrims = []
  const riverLines = D.waterway.features.flatMap(ft => lines(ft.geometry).map(c => ({ c, river: ft.properties.waterway === 'river' })))
  const waterLook = () => new Cesium.MaterialAppearance({ material: Cesium.Material.fromType('Water', {
    baseWaterColor: new Cesium.Color(0.13, 0.32, 0.40, 1), blendColor: new Cesium.Color(0.20, 0.38, 0.36, 1),
    normalMap: Cesium.buildModuleUrl('Assets/Textures/waterNormals.jpg'),
    frequency: 500, animationSpeed: 0.03, amplitude: 3, specularIntensity: 0.45 }) })
  function bufferPolys(c, hw) {   // 선분마다 사각형 + 꺾이는 곳마다 원 → 끊김 없는 강
    const out = [], circ = (p, r) => Array.from({ length: 10 }, (_, i) => { const t = i / 10 * Math.PI * 2; return [p[0] + Math.cos(t) * r / MX, p[1] + Math.sin(t) * r / MY] })
    for (let i = 1; i < c.length; i++) {
      const a = c[i - 1], b = c[i], dx = (b[0] - a[0]) * MX, dy = (b[1] - a[1]) * MY, l = Math.hypot(dx, dy) || 1
      const ox = -dy / l * hw / MX, oy = dx / l * hw / MY
      out.push([[a[0] + ox, a[1] + oy], [b[0] + ox, b[1] + oy], [b[0] - ox, b[1] - oy], [a[0] - ox, a[1] - oy]])
      out.push(circ(b, hw))
    }
    out.push(circ(c[0], hw))
    return out
  }
  function buildRiver(lv) {
    riverPrims.forEach(p => scene.groundPrimitives.remove(p))
    const k = RIVER_SWELL[lv] || 1, polys = riverLines.flatMap(r => bufferPolys(r.c, (r.river ? RIVER_HALF.river : RIVER_HALF.stream) * k))
    riverPrims = [scene.groundPrimitives.add(new Cesium.GroundPrimitive({
      geometryInstances: polys.map(p => new Cesium.GeometryInstance({ geometry: new Cesium.PolygonGeometry({
        polygonHierarchy: new Cesium.PolygonHierarchy(Cesium.Cartesian3.fromDegreesArray(p.flat())), vertexFormat: Cesium.MaterialAppearance.MaterialSupport.TEXTURED.vertexFormat }) })),
      appearance: waterLook(), classificationType: Cesium.ClassificationType.TERRAIN }), 0)]   // 맨 아래에 깔아서 다리 위 대피로가 강물에 덮이지 않게
  }

  /* ============ 건물 (건축물대장) — 물에 잠기는 건물은 파랗게 ============ */
  const blds = [], bInst = []
  D.buildings.features.forEach(ft => rings(ft.geometry).forEach(p => {
    const c = centroid(p[0]), pr = ft.properties
    const b = { zone: [false, inFC(c, D.flood50), inFC(c, D.flood80), inFC(c, D.flood100)], cur: 'n' }
    bInst.push(new Cesium.GeometryInstance({ id: b,
      geometry: new Cesium.PolygonGeometry({ polygonHierarchy: hier(p), height: pr.base, extrudedHeight: pr.base + pr.hgt, vertexFormat: Cesium.PerInstanceColorAppearance.VERTEX_FORMAT }),
      attributes: { color: Cesium.ColorGeometryInstanceAttribute.fromColor(C('#E8E4DC')) } }))
    blds.push(b)
  }))
  const bldPrim = scene.primitives.add(new Cesium.Primitive({ geometryInstances: bInst, appearance: new Cesium.PerInstanceColorAppearance() }))
  const BCOL = { n: Cesium.ColorGeometryInstanceAttribute.toValue(C('#E8E4DC')), w: Cesium.ColorGeometryInstanceAttribute.toValue(C('#5C9AD6')) }
  let curFlood = 0
  function paintBuildings(flood) {
    blds.forEach(b => {
      const k = flood > 0 && b.zone[flood] ? 'w' : 'n'
      if (bldPrim.ready && b.cur !== k) { bldPrim.getGeometryInstanceAttributes(b).color = BCOL[k]; b.cur = k }
    })
    if (!bldPrim.ready) { const rm = scene.postRender.addEventListener(() => { if (bldPrim.ready) { rm(); paintBuildings(curFlood) } }) }
  }
  // 도로는 얇게 (위치 파악용). 농로·작업로·임도·오솔길은 지도를 어지럽혀서 숨김
  const HIDE_ROADS = ['service', 'track', 'path', 'construction']
  D.roads.features.filter(ft => !HIDE_ROADS.includes(ft.properties.highway)).forEach(ft => lines(ft.geometry).forEach(l => viewer.entities.add({ polyline: {
    positions: Cesium.Cartesian3.fromDegreesArray(l.flat()), clampToGround: true,
    width: ['primary', 'secondary', 'trunk'].includes(ft.properties.highway) ? 3 : 1.5, material: C('#FFFFFF', 0.35) } })))

  /* ============ 대피 경로 (양지교 경유 대피소 / 높은 곳) ============ */
  const evac = (D.evac ? D.evac.features : []).map(f => ({ pr: f.properties, pts: lines(f.geometry).flat() }))
  const baseDef = evac.find(r => !/없음/.test(r.pr.bridge || '')) || evac[0] || null
  // 뒷산 산길: 캠핑장에서 바로 서쪽 산으로 올라가는 길. 도로망에 없는 길이라 10m DEM 으로 경사·침수범위·강을 피하는
  // 최소비용 경로를 QGIS 에서 계산한 값 (evac_hill_path.geojson). 실제 길이 아니므로 현장 확인·정비가 필요
  const HILL_PTS = [[128.2061766,38.2258503],[128.2061726,38.2258452],[128.2061645,38.2258349],[128.2061525,38.2258194],[128.2061364,38.2257988],[128.2061163,38.2257731],[128.2060922,38.2257422],[128.2060641,38.2257062],[128.2060319,38.225665],[128.2059976,38.225629],[128.205961,38.2255981],[128.2059221,38.2255724],[128.2058811,38.2255518],[128.2058377,38.2255364],[128.2057922,38.2255261],[128.2057444,38.2255209],[128.2056944,38.2255209],[128.2056491,38.2255194],[128.2056085,38.2255162],[128.2055726,38.2255116],[128.2055413,38.2255053],[128.2055148,38.2254975],[128.2054929,38.2254881],[128.2054757,38.2254772],[128.2054632,38.2254647],[128.2054523,38.2254475],[128.2054429,38.2254256],[128.2054351,38.2253991],[128.2054288,38.2253678],[128.2054241,38.2253319],[128.205421,38.2252912],[128.2054194,38.2252459],[128.2054194,38.2251959],[128.2054163,38.2251537],[128.2054101,38.2251194],[128.2054007,38.2250928],[128.2053882,38.2250741],[128.2053726,38.2250631],[128.2053538,38.22506],[128.2053319,38.2250647],[128.2053069,38.2250772],[128.2052788,38.2250834],[128.2052476,38.2250834],[128.2052132,38.2250772],[128.2051757,38.2250647],[128.2051351,38.2250459],[128.2050913,38.2250209],[128.2050444,38.2249897],[128.2049944,38.2249522],[128.2049507,38.2249194],[128.2049132,38.2248912],[128.2048819,38.2248678],[128.2048569,38.2248491],[128.2048382,38.224835],[128.2048257,38.2248256],[128.2048194,38.2248209]]
  const detourDef = D.hill && D.hill.features && D.hill.features[0]
    ? { pr: D.hill.features[0].properties, pts: lines(D.hill.features[0].geometry).flat() }
    : { pr: { name: '뒷산 산길' }, pts: HILL_PTS }
  // 경로가 다리 위를 지나는 구간 찾기 (OSM 도로의 bridge 속성)
  const bridgeSegs = D.roads.features.filter(f => f.properties.bridge && f.properties.bridge !== 'no').flatMap(f => lines(f.geometry))
  function segDist(p, a, b) {
    const ax = (a[0] - p[0]) * MX, ay = (a[1] - p[1]) * MY, bx = (b[0] - p[0]) * MX, by = (b[1] - p[1]) * MY
    const dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy || 1, t = Math.max(0, Math.min(1, -(ax * dx + ay * dy) / l2))
    return Math.hypot(ax + dx * t, ay + dy * t)
  }
  function makeRoute(def, color, kmh) {
    // 5m 간격으로 다시 나눠 부드럽게 이동
    const raw = def.pts, pts = [raw[0]]
    for (let i = 1; i < raw.length; i++) { const d = dist(raw[i - 1], raw[i]), n = Math.max(1, Math.ceil(d / 5)); for (let k = 1; k <= n; k++) pts.push([raw[i - 1][0] + (raw[i][0] - raw[i - 1][0]) * k / n, raw[i - 1][1] + (raw[i][1] - raw[i - 1][1]) * k / n]) }
    const cum = [0]; for (let i = 1; i < pts.length; i++) cum.push(cum[i - 1] + dist(pts[i - 1], pts[i]))
    const L = cum[cum.length - 1]
    const at = m => {
      m = Math.max(0, Math.min(L, m)); const i = cum.findIndex(c => c >= m); if (i <= 0) return pts[0]
      const t = (m - cum[i - 1]) / (cum[i] - cum[i - 1] || 1)
      return [pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * t, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * t]
    }
    // 다리 구간 (가장 긴 것)
    let best = null, run = null
    pts.forEach((p, i) => {
      const on = bridgeSegs.some(l => l.some((q, k) => k && segDist(p, l[k - 1], q) < 9))
      if (on) { run = run || { a: cum[i] }; run.b = cum[i] } else if (run) { if (!best || run.b - run.a > best.b - best.a) best = run; run = null }
    })
    if (run && (!best || run.b - run.a > best.b - best.a)) best = run
    const main = best && best.b - best.a > 20 ? { m: [best.a, best.b], len: best.b - best.a } : null
    // 화면 표시: 어두운 테두리 + 목적지 쪽으로 흐르는 대피 화살표
    const outline = viewer.entities.add({ polyline: { positions: Cesium.Cartesian3.fromDegreesArray(raw.flat()), clampToGround: true, width: 26, material: C('#06140C', 0.9) } })
    const mat = arrowMaterial(color)
    const prim = groundLine(raw, 19, new Cesium.PolylineMaterialAppearance({ material: mat }))
    const tourSec = Math.max(20, L * 16 / 1000)   // 1.5km → 약 24초
    const sphere = Cesium.BoundingSphere.fromPoints(pts.filter((_, i) => i % 5 === 0).map(p => P3(p[0], p[1])))
    return { def, L, at, pts, main, mat, tourSec, kmh, sphere, min: Math.max(1, Math.round(L / (kmh * 1000) * 60)),
      show(v) { outline.show = v; prim.show = v } }
  }
  // 대피소 길: 평지 걸음(시속 4km). 높은 곳: 오르막이라 시속 2km 로 계산
  const ROUTES = { detour: makeRoute(detourDef, '#14A05A', 2) }
  if (baseDef) ROUTES.base = makeRoute(baseDef, '#14A05A', 4)
  if (ROUTES.base && !ROUTES.base.main) { const L = ROUTES.base.L; ROUTES.base.main = { m: [L * 0.45 - 68, L * 0.45 + 68], len: 137 } }
  if (ROUTES.base) {   // 다리 이름: 경로 속성의 "○○교 137m 경유"에서
    const bn = String(baseDef.pr.bridge || '').replace(/\s*\d+m/, '').replace(/\s*경유$/, '').trim()
    ROUTES.base.main.name = bn && !/없음/.test(bn) ? bn : '다리'
  }
  const showRoute = (k, v) => { if (ROUTES[k]) ROUTES[k].show(v) }
  let routeKey = ROUTES.base ? 'base' : 'detour', RT = ROUTES[routeKey]
  if (ROUTES.base) ROUTES.detour.show(false)
  state.hasBase = !!ROUTES.base
  state.baseLabel = ROUTES.base ? `대피소 (${ROUTES.base.main.name})` : '대피소 (없음)'

  /* ============ 캐릭터 (다리를 번갈아 들며 걷고, 가는 방향을 바라봄) ============ */
  let meU = 0, meTarget = 0, mePos = P3(RT.pts[0][0], RT.pts[0][1]), moving = false, faceRight = false
  const WALK = [0, 1, 2, 1], STEP_MS = 150
  const walkPhase = () => performance.now() / STEP_MS
  const charImg = () => {
    const f = moving && !reduce ? WALK[Math.floor(walkPhase()) % 4] : 1
    return `${base}char/walk${f}${faceRight ? 'm' : ''}.png`
  }
  ;['', 'm'].forEach(s => [0, 1, 2].forEach(f => { new Image().src = `${base}char/walk${f}${s}.png` }))
  viewer.entities.add({
    position: new Cesium.CallbackProperty(() => mePos, false),
    billboard: {
      image: new Cesium.CallbackProperty(charImg, false), width: 54, height: 67, verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
      disableDepthTestDistance: Number.POSITIVE_INFINITY, scaleByDistance: new Cesium.NearFarScalar(250, 1.25, 6000, 0.7),
      pixelOffset: new Cesium.CallbackProperty(() => new Cesium.Cartesian2(0, moving && !reduce ? -Math.abs(Math.sin((walkPhase() + 0.5) * Math.PI / 2)) * 3 : 0), false),
      rotation: new Cesium.CallbackProperty(() => moving && !reduce ? Math.sin((walkPhase() + 0.5) * Math.PI / 2) * 0.06 : 0, false),
    },
  })

  /* ============ 대피소 · 높은 곳 표시 ============ */
  const ptOf = layer => { const f = D.points.features.find(f => f.properties.layer === layer); return f ? f.geometry.coordinates : null }
  const camp = ptOf('campsite') || RT.pts[0]
  const shelter = ROUTES.base ? (ptOf('shelter_alt') || baseDef.pts[baseDef.pts.length - 1]) : null
  const shelterF = D.points.features.find(f => f.properties.layer === 'shelter_alt')
  const shelterName = shelterF ? shelterF.properties.name : '대피소'
  const shelterSub = shelterF ? `${shelterF.properties.level || ''} · 최대 ${shelterF.properties.capacity}명`.replace(/^ · /, '') : ''
  const hEnd = detourDef.pts[detourDef.pts.length - 1]
  const hillGain = Math.round((heightAt(hEnd[0], hEnd[1]) - heightAt(camp[0], camp[1])) / 5) * 5   // 캠핑장보다 몇 m 높은지
  function pin(ll, color, hgt, parent) {
    const g = heightAt(ll[0], ll[1])
    viewer.entities.add({ parent, position: Cesium.Cartesian3.fromDegrees(ll[0], ll[1], g + hgt / 2), cylinder: { length: hgt, topRadius: 2.5, bottomRadius: 2.5, material: C(color) } })
    viewer.entities.add({ parent, position: Cesium.Cartesian3.fromDegrees(ll[0], ll[1], g + hgt + 9), ellipsoid: { radii: new Cesium.Cartesian3(10, 10, 10), material: C(color) } })
  }
  const baseGroup = viewer.entities.add({}), detourGroup = viewer.entities.add({ show: false })
  if (shelter) pin(shelter, '#5FC795', 55, baseGroup)
  pin(hEnd, '#5FC795', 45, detourGroup)

  /* ============ 이름표 (지도 위 HTML) ============ */
  const tags = []
  function tag(text, cls, pos, opt = {}) {
    const el = document.createElement('div'); el.className = 'tag ' + cls; el.textContent = text; tagLayer.appendChild(el)
    const t = Object.assign({ el, p: pos }, opt); tags.push(t); return t
  }
  const meTag = tag('내 위치', 'me', null)
  const dLabel = r => r.L >= 1000 ? (r.L / 1000).toFixed(1) + 'km' : Math.round(r.L / 10) * 10 + 'm'
  if (ROUTES.base) tag(`대피소 · ${shelterName} ${dLabel(ROUTES.base)}`, 'shelter', P3(shelter[0], shelter[1], 80), { baseOnly: true })
  tag(`높은 곳 · 참고 방향 ${dLabel(ROUTES.detour)}`, 'shelter', P3(hEnd[0], hEnd[1], 60), { detourOnly: true })
  const scratch = new Cesium.Cartesian2()
  function placeTags() {
    const W = viewer.canvas.clientWidth, H = viewer.canvas.clientHeight, camPos = camera.positionWC
    tags.forEach(t => {
      let off = !t.p || (t.detourOnly && routeKey !== 'detour') || (t.baseOnly && routeKey !== 'base')
      if (!off) {
        const c = scene.cartesianToCanvasCoordinates(t.p, scratch)
        off = !c || c.x < -40 || c.y < 0 || c.x > W + 40 || c.y > H || Cesium.Cartesian3.distance(camPos, t.p) > 9000
        if (!off) { t.el.style.left = c.x + 'px'; t.el.style.top = c.y + 'px' }
      }
      t.el.hidden = off
    })
  }

  /* ============ 카메라 ============ */
  function fly(ll, heading, pitchDeg, range, done) {
    camera.flyToBoundingSphere(new Cesium.BoundingSphere(P3(ll[0], ll[1]), 30), {
      offset: new Cesium.HeadingPitchRange(heading, Cesium.Math.toRadians(pitchDeg), range), duration: reduce ? 0 : 2.2,
      easingFunction: Cesium.EasingFunction.QUADRATIC_IN_OUT, complete: done })
  }
  function goHome() {   // 지금 경로 전체가 보이게
    const s = RT.sphere
    camera.flyToBoundingSphere(s, { offset: new Cesium.HeadingPitchRange(Cesium.Math.toRadians(20), Cesium.Math.toRadians(routeKey === 'base' ? -38 : -32), Math.max(520, s.radius * 2.9)),
      duration: reduce ? 0 : 2.4, easingFunction: Cesium.EasingFunction.QUADRATIC_IN_OUT })
  }

  /* ============ 단계(안전~심각)와 경로 ============ */
  const ST = CRIT.stages, MAXR = CRIT.max || 200
  state.stages = ST; state.maxRain = MAXR
  const stageFromTable = mm => { let k = 0; ST.forEach((s, i) => { if (mm >= s.from) k = i }); return k }
  const km = m => m < 1000 ? Math.round(m / 10) * 10 + 'm' : (m / 1000).toFixed(2) + 'km'
  const LB = ROUTES.base ? ROUTES.base.L : 0, LD = ROUTES.detour.L
  const hb = (Math.atan2((hEnd[0] - camp[0]) * MX, (hEnd[1] - camp[1]) * MY) * 180 / Math.PI + 360) % 360
  const hDir = ['북', '북동', '동', '남동', '남', '남서', '서', '북서'][Math.round(hb / 45) % 8]
  let level = -1, curStage = -1, step = 0
  let baseSteps = []
  if (ROUTES.base) {
    const B0 = ROUTES.base.main.m[0], B1 = ROUTES.base.main.m[1], bridge = ROUTES.base.main.name
    baseSteps = [
      { m: 40, head: '캠핑장 진입로로 나가 강변 길로 가세요', caution: () => level >= 1 ? '물이 차 있으면 높은 곳으로' : '' },
      { m: Math.max(60, B0 - 60), head: `강변 길을 따라 남쪽 ${bridge}까지 가세요` },
      { m: Math.round((B0 + B1) / 2), head: `${bridge}를 건너세요 (${Math.round(ROUTES.base.main.len)}m)`, caution: () => '물이 넘치면 건너지 말고 높은 곳으로' },
      { m: Math.min(LB - 60, B1 + 200), head: '마을 안 길을 따라 북쪽으로 가세요' },
      { m: LB, head: `${shelterName}에 도착했습니다`, sub: shelterSub },
    ]
  }
  const STEPS = {
    base: baseSteps,
    detour: [
      { m: Math.min(40, LD * 0.2), head: `캠핑장 ${hDir}쪽 높은 곳으로 가세요`, caution: () => '강·계곡 쪽으로 가지 마세요' },
      { m: Math.round(LD * 0.55), head: '비탈을 따라 위로 올라가세요', caution: () => '지형으로 계산한 참고 방향이에요. 실제 길과 다를 수 있어요' },
      { m: LD, head: '높은 곳에 도착했습니다', sub: `캠핑장보다 약 ${hillGain}m 높은 곳 · 119에 위치를 알리세요` },
    ],
  }
  const DEST = { base: shelterName, detour: '높은 곳' }

  function setRoute(key) {
    routeKey = key; RT = ROUTES[key]
    showRoute('base', key === 'base'); showRoute('detour', key === 'detour')
    detourGroup.show = key === 'detour'; baseGroup.show = key === 'base'
    const short = RT.L < 1000
    state.routeKey = key
    state.dest = {
      name: DEST[key],
      meta: key === 'base' ? `대피소 · ${ROUTES.base.main.name} 경유` : `지형으로 계산한 참고 방향 · 캠핑장보다 약 ${hillGain}m 높음`,
      distNum: short ? String(Math.round(RT.L / 10) * 10) : (RT.L / 1000).toFixed(2), distUnit: short ? 'm' : 'km',
      min: `도보 약 ${RT.min}분`,
      sum: `${DEST[key]}까지 ${short ? Math.round(RT.L / 10) * 10 + 'm' : (RT.L / 1000).toFixed(2) + 'km'} · 도보 약 ${RT.min}분`,
    }
    state.detourBtn = key !== 'detour' && !!ROUTES.base
    meU = 0
  }
  function setLevel(lv) {
    level = lv
    state.lv = { ...LV[lv], head: lv === 1 && !ROUTES.base ? '높은 곳으로 피할 준비를 하세요' : LV[lv].head }
    buildRiver(lv)
    // 안전·주의: 대피소 경로. 경계 이상: 다리 경로는 고를 수 없고 바로 높은 곳으로
    const bridgeOff = lv >= 2 || !ROUTES.base
    state.baseDisabled = bridgeOff
    const want = bridgeOff ? 'detour' : 'base'
    if (routeKey !== want) { setRoute(want); if (state.mode === 'nav') { showStep(0); return } meTarget = 0; goHome() }
    if (state.mode === 'nav') showStep(step, true)
  }
  function applyStage(k) {
    const s = ST[k]
    state.stage = { label: s.label, color: s.color, ai: false }
    if (k === curStage) return
    curStage = k; curFlood = s.flood
    WATER.forEach((w, i) => { w.target = i < s.flood ? 0.62 : 0 })
    paintBuildings(s.flood)
    if (s.level !== level || !riverPrims.length) setLevel(s.level)
  }

  /* ============ 길안내 ============ */
  let navWalk = null, tour = null, camHeading = 0
  const NAV_SPEED = 85   // 화면에서 걷는 속도 (m/초, 실제 걸음보다 빠르게 보여 주는 값)
  function stopNavWalk() { if (!navWalk) return; navWalk = null; camera.lookAtTransform(Cesium.Matrix4.IDENTITY) }
  function showStep(i, keepCam) {
    const list = STEPS[routeKey]
    step = Math.max(0, Math.min(list.length - 1, i))
    const s = list[step], L = RT.L, left = L - s.m
    state.nav = {
      count: `${step + 1} / ${list.length}`,
      left: left > 5 ? `${DEST[routeKey]}까지 ${km(left)} · 약 ${Math.max(1, Math.round(left / (RT.kmh * 1000) * 60))}분` : DEST[routeKey],
      no: `${step + 1}단계`, head: s.head, caution: s.caution ? s.caution() : '', sub: s.sub || '',
      bar: s.m / L * 100, prevDisabled: step === 0, nextLabel: step === list.length - 1 ? '안내 마치기' : '다음',
    }
    state.detourBtn = routeKey !== 'detour' && step !== list.length - 1 && !!ROUTES.base
    meTarget = s.m / L
    if (keepCam) return
    const from = meU * L
    if (reduce || Math.abs(s.m - from) < 5) { const p = RT.at(s.m), q = RT.at(Math.min(L, s.m + 40)); fly(p, headingOf(p, q), -38, 330); return }
    // 다음 단계로: 카메라가 캐릭터 뒤로 천천히 날아간 뒤, 캐릭터가 걸어가고 카메라가 따라감
    const dir = s.m > from ? 1 : -1, p = RT.at(from), q = RT.at(Math.max(0, Math.min(L, from + dir * 40)))
    navWalk = { phase: 'fly' }; camHeading = headingOf(p, q)
    fly(p, camHeading, -35, 300, () => { if (navWalk) navWalk.phase = 'walk' })
  }
  function stopTour(home) {
    if (!tour) return; tour = null; camera.lookAtTransform(Cesium.Matrix4.IDENTITY)
    state.tourOn = false
    if (home) { meTarget = 0; goHome() }
  }
  function startTour() {
    if (state.mode === 'nav') return
    if (reduce) { meTarget = 1; fly(RT.at(RT.L), 0, -40, 500); return }
    meU = 0; meTarget = 0; tour = { t0: performance.now() }
    const p = RT.at(0), q = RT.at(60); camHeading = headingOf(p, q)
    state.tourOn = true; state.sheetOpen = false
  }

  /* ============ 지도 돌리기 (나침반) ============ */
  function centerPoint() {
    const ray = camera.getPickRay(new Cesium.Cartesian2(viewer.canvas.clientWidth / 2, viewer.canvas.clientHeight * 0.45))
    return ray && scene.globe.pick(ray, scene)
  }
  function orbit(angle) {
    const c = centerPoint(); if (!c) return
    camera.lookAtTransform(Cesium.Transforms.eastNorthUpToFixedFrame(c))
    camera.rotateRight(angle)
    camera.lookAtTransform(Cesium.Matrix4.IDENTITY)
  }
  let northAnim = null

  /* ============ 매 프레임 ============ */
  // 기울기 제한: 지평선 쪽으로 너무 눕히면 한 번 끌 때 수백 m씩 튀므로, 화면 가운데 지점을 중심으로 -20°까지만
  const MIN_TILT = Cesium.Math.toRadians(-20)
  cleanups.push(scene.preRender.addEventListener(() => {
    if (tour || navWalk) return
    if (camera.pitch <= MIN_TILT) return
    const ray = camera.getPickRay(new Cesium.Cartesian2(scene.canvas.clientWidth / 2, scene.canvas.clientHeight / 2))
    const t = ray && scene.globe.pick(ray, scene)
    if (!t) { camera.setView({ orientation: { heading: camera.heading, pitch: MIN_TILT, roll: 0 } }); return }
    camera.lookAt(t, new Cesium.HeadingPitchRange(camera.heading, MIN_TILT, Cesium.Cartesian3.distance(camera.positionWC, t)))
    camera.lookAtTransform(Cesium.Matrix4.IDENTITY)
  }))
  cleanups.push(scene.preRender.addEventListener(() => {
    if (!northAnim) return
    let h = camera.heading; if (h > Math.PI) h -= 2 * Math.PI
    if (Math.abs(h) < 0.01) { northAnim = null; return }
    orbit(h * (reduce ? 1 : 0.18))
  }))
  let last = performance.now()
  cleanups.push(scene.preRender.addEventListener(() => {
    const now = performance.now(), dt = Math.min(0.1, (now - last) / 1000); last = now
    flowMats.forEach(m => { m.uniforms.time = now / 1000 })
    // 화살표 간격: 화면에서 약 34px마다 하나가 되도록
    ;[ROUTES.base, ROUTES.detour].filter(Boolean).forEach(r => {
      const mpp = camera.getPixelSize(r.sphere, viewer.canvas.clientWidth, viewer.canvas.clientHeight) || 1
      r.mat.uniforms.repeat = Math.max(2, r.L / (34 * mpp))
    })
    // 물이 천천히 차오르거나 빠짐
    WATER.forEach(w => {
      if (!w.prim.empty && Math.abs(w.alpha - w.target) > 0.005) {
        const a = w.alpha + Math.sign(w.target - w.alpha) * Math.min(Math.abs(w.target - w.alpha), dt * 0.35)
        if (setWaterAlpha(w, a)) w.alpha = a
      }
    })
    if (tour) {
      const k = Math.min(1, (now - tour.t0) / (RT.tourSec * 1000))
      meU = meTarget = k
      const m = k * RT.L, p = RT.at(m), q = RT.at(Math.min(RT.L, m + 60))
      let d = headingOf(p, q) - camHeading; d = Math.atan2(Math.sin(d), Math.cos(d)); camHeading += d * Math.min(1, dt * 2)
      camera.lookAt(P3(p[0], p[1], 2), new Cesium.HeadingPitchRange(camHeading, Cesium.Math.toRadians(-24), 230))
      if (k >= 1) { stopTour(false); fly(RT.at(RT.L), camHeading, -35, 420) }
    } else if (navWalk) {
      // 길안내: 일정한 속도로 걸어가고, 카메라는 뒤에서 부드럽게 따라감
      if (navWalk.phase === 'walk') {
        const stepLen = NAV_SPEED * dt / RT.L, d = meTarget - meU
        meU = Math.abs(d) <= stepLen ? meTarget : meU + Math.sign(d) * stepLen
        const m = meU * RT.L, dir = Math.sign(d) || 1, p = RT.at(m), q = RT.at(Math.max(0, Math.min(RT.L, m + dir * 40)))
        let dh = headingOf(p, q) - camHeading; dh = Math.atan2(Math.sin(dh), Math.cos(dh)); camHeading += dh * Math.min(1, dt * 1.2)
        camera.lookAt(P3(p[0], p[1], 2), new Cesium.HeadingPitchRange(camHeading, Cesium.Math.toRadians(-35), 300))
        if (meU === meTarget) stopNavWalk()
      }
    } else {
      meU += (meTarget - meU) * (reduce ? 1 : Math.min(1, dt * 1.6))
    }
    const mp = RT.at(meU * RT.L)
    moving = !!tour || (navWalk ? navWalk.phase === 'walk' : Math.abs(meTarget - meU) * RT.L > 2)
    mePos = P3(mp[0], mp[1], 0.5)
    meTag.p = mePos
    // 화면에서 오른쪽으로 가면 좌우 반전된 그림 (얼굴이 가는 쪽을 보게)
    if (moving) {
      const ahead = RT.at(Math.min(RT.L, meU * RT.L + 8)), back = RT.at(Math.max(0, meU * RT.L - 8))
      const a = scene.cartesianToCanvasCoordinates(P3(ahead[0], ahead[1])), b = scene.cartesianToCanvasCoordinates(P3(back[0], back[1]))
      if (a && b && Math.abs(a.x - b.x) > 1.5) faceRight = a.x > b.x
    }
    state.needle = -Cesium.Math.toDegrees(camera.heading)
  }))
  cleanups.push(scene.postRender.addEventListener(placeTags))

  // 지도를 만지면: 시트를 접고(지도가 가려지지 않게), 따라가기·길안내 걷기를 멈춤
  const onCanvasDown = () => {
    state.sheetOpen = false
    stopTour(false)
    if (navWalk) { camera.cancelFlight(); stopNavWalk() }
  }
  viewer.canvas.addEventListener('pointerdown', onCanvasDown)

  /* ============ 시작 ============ */
  setRoute(routeKey)
  buildRiver(1)
  // 기상특보 (예시) → 특보의 예상 강수량으로 시작
  const a = SAMPLE_ALERT, t = new Date(a.issuedAt)
  state.alert = {
    title: `${a.kind} ${a.action}`, warn: a.level === 'warning',
    time: `${t.getMonth() + 1}.${t.getDate()} ${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')} · 인제군`,
    area: '대상: ' + a.area, body: a.body, src: '출처: ' + a.source,
  }
  state.rain = a.rain3h
  applyStage(stageFromTable(a.rain3h))
  {
    const other = shelter || hEnd, cx = (camp[0] + other[0]) / 2, cy = (camp[1] + other[1]) / 2
    camera.setView({ destination: Cesium.Cartesian3.fromDegrees(cx, cy - 0.03, 4000), orientation: { heading: 0, pitch: Cesium.Math.toRadians(-45) } })
  }
  goHome()
  // 첫 타일이 다 들어오면 로딩 화면 제거
  {
    let ok = 0; const t0 = performance.now()
    const timer = setInterval(() => {
      const w = performance.now() - t0; ok = scene.globe.tilesLoaded ? ok + 1 : 0
      if (w < 8000 && (w < 2500 || ok < 4)) return
      clearInterval(timer); state.loading = false
    }, 300)
    cleanups.push(() => clearInterval(timer))
  }

  /* ============ 화면(Vue)이 부르는 동작 ============ */
  return {
    setRain(mm) { state.rain = mm; applyStage(stageFromTable(mm)) },
    selectRoute(key) { if (key === 'base' && state.baseDisabled) return; setRoute(key); meTarget = 0; goHome() },
    where() { state.sheetOpen = false; fly(routeKey === 'base' ? shelter : hEnd, camera.heading, -35, 520) },
    startNav() { stopTour(false); meU = meTarget = 0; state.mode = 'nav'; state.sheetOpen = true; showStep(0) },
    prev() { showStep(step - 1) },
    next() { if (step === STEPS[routeKey].length - 1) this.endNav(); else showStep(step + 1) },
    endNav() { stopNavWalk(); state.mode = 'overview'; meTarget = 0; state.detourBtn = routeKey !== 'detour' && !!ROUTES.base; goHome() },
    detour() { setRoute('detour'); meU = 0; showStep(0) },
    toggleTour() { if (tour) stopTour(true); else startTour() },
    // 나침반: 좌우로 끌면 회전, 그냥 누르면 북쪽이 위로
    compassStart() { stopTour(false); northAnim = null },
    compassMove(dx) { orbit(dx * 0.012) },
    compassNorth() { northAnim = true },
    compassKey(dir) { orbit(dir * 0.2) },
    destroy() {
      if (destroyed) return; destroyed = true
      cleanups.forEach(fn => fn())
      viewer.canvas.removeEventListener('pointerdown', onCanvasDown)
      tags.forEach(t => t.el.remove())
      viewer.destroy()
    },
  }
}
