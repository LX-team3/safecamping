// 재난 대피 3D 지도 엔진 (CesiumJS)
// disaster-map/app/index.html 의 지도 코드를 Vue 화면에서 쓰도록 옮긴 것.
// - 화면(Vue)은 state(반응형 객체)를 읽어 그리고, 버튼은 아래 actions 를 부른다
// - 지도는 createDisasterMap() 으로 만들고, 화면을 떠날 때 destroy() 로 정리한다 (메모리 누수 방지)
// - 인북천 물빛테마공원 캠핑장 1곳 데이터(public/disaster-map/data.json) 기준
import * as Cesium from 'cesium'
import 'cesium/Build/Cesium/Widgets/widgets.css'

// Cesium 작업 파일(Workers·Assets) 위치 — scripts/copy-cesium.mjs 가 public/cesium 에 복사해 둔다
window.CESIUM_BASE_URL = import.meta.env.BASE_URL + 'cesium/'

// 색은 styles/tokens.css 변수만 쓴다 (팀 규칙). Cesium 은 CSS 를 모르므로 변수 값을 읽어서 넘긴다
const token = name => getComputedStyle(document.documentElement).getPropertyValue(name).trim()
// 화면(Vue) 쪽에는 'var(--color-stage-1)' 처럼 넘겨서 CSS 가 칠하게 한다
const cssVar = name => `var(${name})`

// criteria.json 을 못 읽을 때 쓰는 같은 기준표 (3시간 동안 내린 비, mm)
// 60·90mm = 기상청 호우주의보·경보 기준, 114mm = 서화 관측소 50년에 한 번 올 큰비(WAMIS 설계강수량, 3시간)
// 하천범람지도도 50년 빈도라서 114mm 부터 50년 홍수 범위에 물을 채운다. 80·100년은 할 일이 같아 단계를 두지 않음
const DEFAULT_CRIT = {
  max: 160,
  stages: [
    { from: 0, label: '안전', color: '--color-stage-0', level: 0, flood: 0 },
    { from: 60, label: '준비', color: '--color-stage-1', level: 1, flood: 0, alert: '주의보' },
    { from: 90, label: '대피', color: '--color-stage-2', level: 2, flood: 0, alert: '경보' },
    { from: 114, label: '대피', color: '--color-stage-2', level: 3, flood: 1, alert: '침수' },
  ],
}

// 단계별 안내 문구 (level 0~3). 단계 이름은 disaster-map/rag 단계 문서의 "판단" 과 같게 맞춤
// head 는 "지금 무엇을 하면 되나"를 바로 알 수 있게 할 일로 씀 (2026-11-21, 발표 슬라이드의 단계별 할 일과 같음)
// 2026-11-21 화면 단계는 안전·준비·대피 셋. level 3(114mm, 50년 큰비)은 이름은 '대피' 그대로, 캠핑장까지 잠겨 할 일만 더 급하게
// 높은 곳: 도로로는 1.5km 를 더 낮은 강가로 돌아가야 해서(데이터 폴더 road_route.py) 가장 가까운 산으로 안내. 실제 길은 현장 확인 전
// {dir} 은 캠핑장에서 높은 곳 쪽 방향(남서 등)으로 바꿔 넣는다
// todo: 단계별 행동요령 3줄 (아래 시트 "○○ 단계 행동요령")
const LV = {
  0: { badge: '안전', color: cssVar('--color-stage-0'), head: '대피소 가는 길을 미리 봐 두세요', cta: '대피경로 보기',
    todo: ['대피소 가는 길과 산으로 오르는 방향을 미리 봐 둡니다.', '강가·계곡 가까이에 텐트를 치지 않습니다.', '기상특보와 재난문자를 확인합니다.'] },
  1: { badge: '준비', color: cssVar('--color-stage-1'), head: '짐을 싸고 떠날 준비를 하세요', cta: '대피경로 보기',
    todo: ['짐을 싸고 바로 나갈 수 있게 둡니다.', '손전등·비옷을 챙기고 휴대폰을 충전합니다.', '강물이 불어나는지 자주 살핍니다.'] },
  2: { badge: '대피', color: cssVar('--color-stage-2'), head: '다리를 건너지 말고 높은 곳으로 대피하세요', cta: '대피경로 보기',
    todo: ['짐은 가볍게, 걸어서 산 쪽으로 갑니다.', '물이 넘친 길과 다리는 건너지 않습니다.', '혼자 가지 말고 함께 움직입니다.'] },
  3: { badge: '대피', color: cssVar('--color-stage-2'), head: '짐 두고 지금 바로 높은 곳으로!', cta: '대피경로 보기',
    todo: ['짐 두고 지금 바로 산으로 올라갑니다.', '흐르는 물은 얕아 보여도 건너지 않습니다.', '도착하면 119에 위치를 알립니다.'] },
}

// 화면이 읽는 상태의 처음 모양
export function initialState() {
  return {
    loading: true, error: '',
    alert: { title: '기상특보 확인 중', time: '', area: '', body: '', src: '', warn: false, off: true },
    // 실시간(기상청 실황) 상태. status: loading → ok(받음) · none(못 받음). on: 지금 실시간 값을 보여 주는 중(막대를 만지면 false = 미리 보기)
    live: { status: 'loading', on: false, time: '', reasons: [] },
    rain: 60, maxRain: DEFAULT_CRIT.max, stages: DEFAULT_CRIT.stages.map(s => ({ ...s, color: cssVar(s.color) })),
    waterText: '',   // 막대 아래 한 줄: 지금 보이는 물이 무엇인지
    stage: { label: '준비', color: cssVar('--color-stage-1'), ai: false },
    lv: { ...LV[1] },
    routeKey: 'base', baseDisabled: false, baseLabel: '대피소',
    dest: { name: '', meta: '', distNum: '', distUnit: 'km', min: '', navUrl: '' },
    mode: 'overview',
    nav: { count: '', left: '', no: '', head: '', caution: '', sub: '', bar: 0, prevDisabled: true, nextLabel: '다음' },
    detourBtn: false,
    noRoute: false,   // 침수(level 3): 경로·목적지·길안내 숨김
    sheetOpen: false,
    todo: [...LV[1].todo],
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
  // 두 파일을 동시에 받는다 (차례로 받으면 기다리는 시간이 더해짐)
  const [D, CRIT] = await Promise.all([
    fetch(base + 'data.json').then(r => {
      if (!r.ok) throw new Error('지도 데이터를 불러오지 못했어요')
      return r.json()
    }),
    fetch(base + 'criteria.json', { cache: 'no-cache' }).then(r => r.json()).catch(() => DEFAULT_CRIT),
  ])
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
  // 값이 클수록 타일을 덜 받는다 (2 = Cesium 기본값). 3 이면 아주 약간 흐려지는 대신 받는 타일이 줄어든다
  scene.globe.maximumScreenSpaceError = 3
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
  // C('--color-evac') → tokens.css 의 색을 Cesium 색으로
  const C = (name, a = 1) => Cesium.Color.fromCssColorString(token(name)).withAlpha(a)
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
      uniforms: { color: C(hex), arrow: C('--color-map-line'), repeat: 40, speed: 0.6, time: 0 },
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

  /* ============ 차오르는 물 (하천범람지도 = 50년에 한 번 오는 홍수 때 잠기는 범위) ============ */
  // 땅에 색만 칠하면 물로 안 보여서, 범위 가장자리의 땅 높이로 물 면을 만들고 땅 아래에서 천천히 올라오게 한다
  // 흙탕물처럼 보이지 않게 파란 물 + 잔물결 밝은 줄. 반투명은 그림자와 같이 쓰면 안 보여서 불투명
  const RISE = 8   // 처음에 땅 아래 몇 m 에서 시작해 올라오는지
  const water3d = [D.flood50, D.flood80, D.flood100].map(fc => {
    const pos = r => r.map(([lo, la]) => Cesium.Cartesian3.fromDegrees(lo, la, heightAt(lo, la) + 0.3))
    const insts = fc.features.flatMap(ft => rings(ft.geometry).map(p => new Cesium.GeometryInstance({
      geometry: new Cesium.PolygonGeometry({ polygonHierarchy: new Cesium.PolygonHierarchy(pos(p[0]), p.slice(1).map(h => new Cesium.PolygonHierarchy(pos(h)))),
        perPositionHeight: true, vertexFormat: Cesium.MaterialAppearance.MaterialSupport.TEXTURED.vertexFormat }) })))
    if (!insts.length) return null
    const prim = scene.primitives.add(new Cesium.Primitive({ geometryInstances: insts, show: false, asynchronous: true,
      appearance: new Cesium.MaterialAppearance({ translucent: false, faceForward: true, material: new Cesium.Material({ fabric: {
        uniforms: { color: C('--color-river'), hi: C('--color-river-blend'), time: 0 },   // 강물과 같은 색 (넘친 물 = 강물)
        source: `czm_material czm_getMaterial(czm_materialInput m) {
          czm_material o = czm_getDefaultMaterial(m);
          vec2 p = m.st * 900.0;
          float w = sin(p.x + time * 1.3) * cos(p.y * 0.8 - time) * 0.5 + 0.5;
          o.diffuse = mix(color.rgb * 0.85, hi.rgb, smoothstep(0.75, 1.0, w) * 0.5);
          o.specular = 0.6; o.shininess = 60.0;
          o.alpha = color.a;
          return o; }` } }) }) }))
    const c = fc.features[0] ? rings(fc.features[0].geometry)[0][0][0] : [0, 0]
    const up = Cesium.Cartesian3.normalize(Cesium.Cartesian3.fromDegrees(c[0], c[1]), new Cesium.Cartesian3())
    return { prim, up, off: -RISE, target: -RISE }
  })
  function setWater3d(flood) {   // flood: 0 = 물 없음, 1 = 50년 범위
    water3d.forEach((w, i) => {
      if (!w) return
      const on = i === flood - 1
      if (on && !w.prim.show && !(floodDrape[i] && floodDrape[i].show)) { w.prim.show = true; w.off = -RISE }   // 이미 다 찼으면 다시 올리지 않음
      w.target = on ? 0 : -RISE
      // 다 차오른 뒤에는 강물과 같은 층(floodDrape)만 보여 준다. 내려갈 때는 그 층을 끄고 3D 물이 다시 가라앉는다
      if (!on && floodDrape[i] && floodDrape[i].show) { floodDrape[i].show = false; w.prim.show = true; w.off = 0; syncZone() }
    })
  }
  function stepWater3d(dt, sec) {
    water3d.forEach((w, i) => {
      if (!w || !w.prim.show) return
      w.prim.appearance.material.uniforms.time = sec
      const d = w.target - w.off
      if (Math.abs(d) < 0.01) {
        if (w.target < 0) w.prim.show = false
        else if (floodDrape[i]) { floodDrape[i].show = true; w.prim.show = false; syncZone() }
        return
      }
      w.off += Math.sign(d) * Math.min(Math.abs(d), dt * 2.2)   // 초당 약 2.2m
      w.prim.modelMatrix = Cesium.Matrix4.fromTranslation(Cesium.Cartesian3.multiplyByScalar(w.up, w.off, new Cesium.Cartesian3()))
    })
  }

  /* ============ 강 (인북천) — 중심선에 폭을 줘서 물결치는 수면으로 ============ */
  // 평소 강폭의 절반(m). stream = OSM 의 이름 없는 작은 물길(도랑·개천)
  // 작은 물길을 5m(폭 10m)로 그렸더니 인북천처럼 보이고, 바로 옆 대피소(서화중앙교회)가 물 위에 있는 것처럼 보여서 2m(폭 4m)로 줄임
  const RIVER_HALF = { river: 18, stream: 2 }
  const RIVER_SWELL = { 0: 1, 1: 1.35, 2: 1.7, 3: 2.1 } // 비가 올수록 넓어짐 (인북천만. 작은 물길은 넓히지 않음)
  let riverPrims = []
  const riverLines = D.waterway.features.flatMap(ft => lines(ft.geometry).map(c => ({ c, river: ft.properties.waterway === 'river' })))
  const waterLook = () => new Cesium.MaterialAppearance({ material: Cesium.Material.fromType('Water', {
    baseWaterColor: C('--color-river'), blendColor: C('--color-river-blend'),
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
  // 작은 물길은 물결 없이 옅은 색 띠로만 그린다 (인북천과 한눈에 구분되게)
  const streamLook = () => new Cesium.MaterialAppearance({ material: Cesium.Material.fromType('Color', { color: C('--color-river', 0.55) }) })
  function buildRiver(lv) {
    riverPrims.forEach(p => scene.groundPrimitives.remove(p))
    const k = RIVER_SWELL[lv] || 1
    const toPrim = (polys, appearance) => scene.groundPrimitives.add(new Cesium.GroundPrimitive({
      geometryInstances: polys.map(p => new Cesium.GeometryInstance({ geometry: new Cesium.PolygonGeometry({
        polygonHierarchy: new Cesium.PolygonHierarchy(Cesium.Cartesian3.fromDegreesArray(p.flat())), vertexFormat: Cesium.MaterialAppearance.MaterialSupport.TEXTURED.vertexFormat }) })),
      appearance, classificationType: Cesium.ClassificationType.TERRAIN }), 0)   // 맨 아래에 깔아서 다리 위 대피로가 강물에 덮이지 않게
    const riverPolys = riverLines.filter(r => r.river).flatMap(r => bufferPolys(r.c, RIVER_HALF.river * k))
    const streamPolys = riverLines.filter(r => !r.river).flatMap(r => bufferPolys(r.c, RIVER_HALF.stream))
    riverPrims = [riverPolys.length && toPrim(riverPolys, waterLook()), streamPolys.length && toPrim(streamPolys, streamLook())].filter(Boolean)
  }
  // 범람 범위 전체를 땅에 강물로 덮는 층. 위의 3D 물 면은 가장자리 높이로 판판하게 만들어서
  // 둑·둔치·캠핑장처럼 살짝 높은 땅이 물을 뚫고 나와 안 잠긴 것처럼 보였다 → 범위 안은 빠짐없이 물로 보이게
  // flood50fill: 범람지도는 강 구역(강~둑 사이 낮은 땅)을 범위에서 빼서, 캠핑장만 잠기고 강 쪽은 마른 것처럼 보였다.
  // 강에서 시작해 "가까운 범람 가장자리 물 높이보다 낮은 땅"을 이어 채운 칸들(10m DEM, 가장자리에서 300m 안). [서,남,동,북] 사각형
  const FILL = [D.flood50fill || [], [], []]
  const floodDrape = [D.flood50, D.flood80, D.flood100].map((fc, k) => {
    const geo = p => new Cesium.GeometryInstance({ geometry: new Cesium.PolygonGeometry({ polygonHierarchy: p, vertexFormat: Cesium.MaterialAppearance.MaterialSupport.TEXTURED.vertexFormat }) })
    const insts = fc.features.flatMap(ft => rings(ft.geometry).map(p => geo(hier(p))))
      .concat(FILL[k].map(([w, s, e, n]) => geo(new Cesium.PolygonHierarchy(Cesium.Cartesian3.fromDegreesArray([w, s, e, s, e, n, w, n])))))
    return insts.length ? scene.groundPrimitives.add(new Cesium.GroundPrimitive({ geometryInstances: insts, show: false,
      appearance: waterLook(), classificationType: Cesium.ClassificationType.TERRAIN })) : null
  })
  // 강 구역(공식 범람지도가 빼놓은 강 쪽 낮은 땅 = flood50fill)이 70 → 80 → 90 → 100 → 110mm 로 10mm 마다 차오른다 (2026-11-25)
  // 안전·60mm 는 안 차고, 강 구역 밖(마을·캠핑장)은 114mm 이상의 공식 50년 범람 범위(floodDrape)에서만 잠긴다
  // 순서는 실제 땅 높이(낮은 땅)와 강까지 거리로, 켜는 양은 보기 좋게 정한 값 = 참고 그림 (disaster-map/qgis/flood_steps.py 가 floodFillSteps 를 만듦)
  //   강물 높이(REM)로 강 구역 밖까지 채워 봤더니 10m 지형이 둑을 못 잡아 55mm 에 마을이 잠겨서 뺐다
  const riverZones = (D.floodFillSteps || (FILL[0].length ? [FILL[0]] : [])).map(rects => scene.groundPrimitives.add(new Cesium.GroundPrimitive({ show: false,
    geometryInstances: rects.map(([w, s, e, n]) => new Cesium.GeometryInstance({ geometry: new Cesium.PolygonGeometry({
      polygonHierarchy: new Cesium.PolygonHierarchy(Cesium.Cartesian3.fromDegreesArray([w, s, e, s, e, n, w, n])), vertexFormat: Cesium.MaterialAppearance.MaterialSupport.TEXTURED.vertexFormat }) })),
    appearance: waterLook(), classificationType: Cesium.ClassificationType.TERRAIN })))
  // 큰비 층(floodDrape)이 켜지면 겹쳐 진해지지 않게 끈다. 실시간에서 특보·수위로 대피가 되면 비가 적어도 90mm 모습은 보여 준다
  // 막대 아래 회색 문장: 60mm 부터 같은 문장(안전 단계는 없음). 실시간은 특보·수위로도 단계가 오르므로 비의 양을 앞세우지 않고, 물이 보일 때만 낸다
  // 114mm 이상은 공식 50년 하천범람지도, 그 전 물 그림은 양을 계산한 것이 아닌 참고 그림이다
  const waterTextOf = n => state.live.on
    ? (curFlood > 0 || n ? '여기까지 잠길 수 있어요' : '')
    : state.rain >= 60 ? `3시간 동안 ${state.rain}mm가 오면 여기까지 잠길 수 있어요` : ''
  const syncZone = () => {
    const byRain = Math.max(0, Math.min(5, Math.floor((state.rain - 70) / 10) + 1))   // 70→1 · 80→2 · 90→3 · 100→4 · 110→5
    const n = level >= 3 ? 5 : level === 2 ? Math.max(3, byRain) : level === 1 ? Math.min(2, byRain) : 0
    const off = floodDrape[0] && floodDrape[0].show
    riverZones.forEach((z, i) => { z.show = !off && i < n })
    state.waterText = waterTextOf(n)
  }

  /* ============ 건물 (건축물대장) — 물에 잠기는 건물은 파랗게 ============ */
  const blds = [], bInst = []
  D.buildings.features.forEach(ft => rings(ft.geometry).forEach(p => {
    const c = centroid(p[0]), pr = ft.properties
    const b = { zone: [false, inFC(c, D.flood50), inFC(c, D.flood80), inFC(c, D.flood100)], cur: 'n' }
    bInst.push(new Cesium.GeometryInstance({ id: b,
      geometry: new Cesium.PolygonGeometry({ polygonHierarchy: hier(p), height: pr.base, extrudedHeight: pr.base + pr.hgt, vertexFormat: Cesium.PerInstanceColorAppearance.VERTEX_FORMAT }),
      attributes: { color: Cesium.ColorGeometryInstanceAttribute.fromColor(C('--color-building')) } }))
    blds.push(b)
  }))
  const bldPrim = scene.primitives.add(new Cesium.Primitive({ geometryInstances: bInst, appearance: new Cesium.PerInstanceColorAppearance() }))
  bldPrim.shadows = Cesium.ShadowMode.ENABLED   // 건물 그림자 (실제 높이가 있다는 게 보이게)
  const BCOL = { n: Cesium.ColorGeometryInstanceAttribute.toValue(C('--color-building')), w: Cesium.ColorGeometryInstanceAttribute.toValue(C('--color-flood-wet')) }
  let curFlood = 0
  function paintBuildings(flood) {
    blds.forEach(b => {
      const k = flood > 0 && b.zone[flood] ? 'w' : 'n'
      if (bldPrim.ready && b.cur !== k) { bldPrim.getGeometryInstanceAttributes(b).color = BCOL[k]; b.cur = k }
    })
    if (!bldPrim.ready) { const rm = scene.postRender.addEventListener(() => { if (bldPrim.ready) { rm(); paintBuildings(curFlood) } }) }
  }
  // 도로는 얇게 (위치 파악용). 농로·작업로·임도·오솔길은 지도를 어지럽혀서 숨김
  // 자전거길·보행로도 숨김: 강을 가로지르는 자전거 다리가 물 위 하얀 줄처럼 보여서 헷갈림
  const HIDE_ROADS = ['service', 'track', 'path', 'construction', 'cycleway', 'footway']
  // 다리가 아닌데 강을 가로지르는 도로(여울길·잘못 그려진 선)도 강물 위 하얀 줄로 보여서 숨김. 10m마다 찍어 강 안에 들어가면 제외
  const overRiver = l => l.some((q, k) => {
    if (!k) return false
    const a = l[k - 1], n = Math.max(1, Math.ceil(dist(a, q) / 10))
    for (let i = 0; i <= n; i++) {
      const p = [a[0] + (q[0] - a[0]) * i / n, a[1] + (q[1] - a[1]) * i / n]
      if (riverLines.some(r => r.river && r.c.some((c, j) => j && segDist(p, r.c[j - 1], c) < RIVER_HALF.river))) return true
    }
    return false
  })
  D.roads.features.filter(ft => !HIDE_ROADS.includes(ft.properties.highway)).forEach(ft => lines(ft.geometry)
    .filter(l => (ft.properties.bridge && ft.properties.bridge !== 'no') || !overRiver(l))
    .forEach(l => viewer.entities.add({ polyline: {
      positions: Cesium.Cartesian3.fromDegreesArray(l.flat()), clampToGround: true,
      width: ['primary', 'secondary', 'trunk'].includes(ft.properties.highway) ? 3 : 1.5, material: C('--color-map-line', 0.35) } })))

  /* ============ 대피 경로 (양지교 경유 대피소 / 높은 곳) ============ */
  const evac = (D.evac ? D.evac.features : []).map(f => ({ pr: f.properties, pts: lines(f.geometry).flat() }))
  const baseDef = evac.find(r => !/없음/.test(r.pr.bridge || '')) || evac[0] || null
  // 뒷산 산길 = 캠핑장에서 걸어서 가장 빨리 닿는 높은 곳 (10m DEM, 토블러 걷기 속도로 최단 시간 계산)
  // 도착 기준: 이 지도 범람 범위에서 가장 높은 물 높이(floodZ.max 311.1m)보다 높고 100년 범람 범위 밖인 첫 칸
  // 출발점(캠핑장 안쪽)에서 약 108m, 약 21m 높음, 걸어서 약 3분. 지형으로 계산한 방향이지 실제 길이 아니므로 현장 확인·정비가 필요
  // 다시 계산: 데이터 폴더 hill_fastest.py (출발점을 바꾸면 이 줄도 다시 만든다)
  const HILL_PTS = [[128.2060817,38.2252365],[128.2060194,38.2252209],[128.2059194,38.2252209],[128.2058194,38.2252209],[128.2057194,38.2252209],[128.2056194,38.2252209],[128.2055194,38.2252209],[128.2055194,38.2251209],[128.2055194,38.2250209],[128.2055194,38.2249209],[128.2055194,38.2248209],[128.2055194,38.2247209]]
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
    const outline = viewer.entities.add({ polyline: { positions: Cesium.Cartesian3.fromDegreesArray(raw.flat()), clampToGround: true, width: 26, material: C('--color-evac-edge', 0.9) } })
    const mat = arrowMaterial(color)
    const prim = groundLine(raw, 19, new Cesium.PolylineMaterialAppearance({ material: mat }))
    // 물이 찼을 때용 3D 선: 땅에 붙는 선은 수면 아래로 묻히므로, 지형 높이를 따라가는 선을 따로 만들고
    // depthFailAppearance 로 물·지형에 가려진 부분도 그대로 그린다 (대피로는 어떤 상황에서도 보여야 함)
    const line3d = (dz, width, m) => scene.primitives.add(new Cesium.Primitive({ show: false, asynchronous: false,
      geometryInstances: new Cesium.GeometryInstance({ geometry: new Cesium.PolylineGeometry({ width, arcType: Cesium.ArcType.NONE,
        positions: pts.map(p => P3(p[0], p[1], dz)), vertexFormat: Cesium.PolylineMaterialAppearance.VERTEX_FORMAT }) }),
      appearance: new Cesium.PolylineMaterialAppearance({ material: m }),
      depthFailAppearance: new Cesium.PolylineMaterialAppearance({ material: m }) }))
    const out3 = line3d(1.5, 16, Cesium.Material.fromType('Color', { color: C('--color-evac-edge', 0.9) }))
    const arr3 = line3d(1.8, 11, mat)
    const sphere = Cesium.BoundingSphere.fromPoints(pts.filter((_, i) => i % 5 === 0).map(p => P3(p[0], p[1])))
    return { def, L, at, pts, main, mat, kmh, sphere, min: Math.max(1, Math.round(L / (kmh * 1000) * 60)), vis: true,
      show(v) { this.vis = v; const on = v && !noRoute; outline.show = prim.show = on && !routeOver; out3.show = arr3.show = on && routeOver } }
  }
  let routeOver = false   // 물이 찼으면 true → 대피로를 물 위로 그림
  // 침수(level 3, 114mm 이상): 대피 경로·목적지·길안내를 지도와 시트에서 뺀다 (2026-11-21 사용자 결정).
  // 이때는 물이 캠핑장까지 찬 모습과 "짐 두고 바로 산으로" 할 일만 보여 준다. 길 안내는 대피 단계(90mm)에서 한다
  let noRoute = false
  function setRouteOver(on) { if (routeOver === on) return; routeOver = on; Object.values(ROUTES).forEach(r => r.show(r.vis)) }
  // 대피소 길: 평지 걸음(시속 4km). 높은 곳: 오르막이라 시속 2km 로 계산
  const ROUTES = { detour: makeRoute(detourDef, '--color-evac', 2) }
  if (baseDef) ROUTES.base = makeRoute(baseDef, '--color-evac', 4)
  if (ROUTES.base && !ROUTES.base.main) { const L = ROUTES.base.L; ROUTES.base.main = { m: [L * 0.45 - 68, L * 0.45 + 68], len: 137 } }
  if (ROUTES.base) {   // 다리 이름: 경로 속성의 "○○교 137m 경유"에서
    const bn = String(baseDef.pr.bridge || '').replace(/\s*\d+m/, '').replace(/\s*경유$/, '').trim()
    ROUTES.base.main.name = bn && !/없음/.test(bn) ? bn : '다리'
  }
  const showRoute = (k, v) => { if (ROUTES[k]) ROUTES[k].show(v) }
  let routeKey = ROUTES.base ? 'base' : 'detour', RT = ROUTES[routeKey]
  if (ROUTES.base) ROUTES.detour.show(false)
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
      // 지형·경로를 가리지 않게 작게
      image: new Cesium.CallbackProperty(charImg, false), width: 36, height: 45, verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
      disableDepthTestDistance: Number.POSITIVE_INFINITY, scaleByDistance: new Cesium.NearFarScalar(250, 1.0, 6000, 0.6),
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
  if (shelter) pin(shelter, '--color-stage-0', 55, baseGroup)
  pin(hEnd, '--color-stage-0', 45, detourGroup)

  /* ============ 이름표 (지도 위 HTML) ============ */
  const tags = []
  function tag(text, cls, pos, opt = {}) {
    const el = document.createElement('div'); el.className = 'tag ' + cls; el.textContent = text; tagLayer.appendChild(el)
    const t = Object.assign({ el, p: pos }, opt); tags.push(t); return t
  }
  const meTag = tag('내 위치', 'me', null)
  const dLabel = r => r.L >= 1000 ? (r.L / 1000).toFixed(1) + 'km' : Math.round(r.L / 10) * 10 + 'm'
  if (ROUTES.base) tag(`대피소 · ${shelterName} ${dLabel(ROUTES.base)}`, 'shelter', P3(shelter[0], shelter[1], 80), { baseOnly: true })
  tag(`산 위 높은 곳 · 길 확인 전 ${dLabel(ROUTES.detour)}`, 'shelter', P3(hEnd[0], hEnd[1], 60), { detourOnly: true })
  const scratch = new Cesium.Cartesian2()
  function placeTags() {
    const W = viewer.canvas.clientWidth, H = viewer.canvas.clientHeight, camPos = camera.positionWC
    tags.forEach(t => {
      let off = !t.p || (t.detourOnly && (routeKey !== 'detour' || noRoute)) || (t.baseOnly && (routeKey !== 'base' || noRoute))
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
    // 높은 곳 길은 캠핑장 서쪽 비탈을 오르는 길이라, 남남서에서 보면 산등성이에 가려진다
    // → 동북동(강·캠핑장 쪽) 하늘에서 서남서를 내려다봐서 비탈을 오르는 모습이 보이게 한다 (2026-11-21)
    const s = RT.sphere, base = routeKey === 'base'
    camera.flyToBoundingSphere(s, { offset: new Cesium.HeadingPitchRange(Cesium.Math.toRadians(base ? 20 : 250), Cesium.Math.toRadians(base ? -38 : -45), Math.max(520, s.radius * 2.9)),
      duration: reduce ? 0 : 2.4, easingFunction: Cesium.EasingFunction.QUADRATIC_IN_OUT })
  }

  /* ============ 단계(안전·준비·대피)와 경로 ============ */
  const ST = CRIT.stages, MAXR = CRIT.max || 160
  // criteria.json 의 color 는 tokens.css 변수 이름 → 화면에는 var(...) 로 넘김
  const stageColor = s => s.color && s.color.startsWith('--') ? cssVar(s.color) : s.color
  state.stages = ST.map(s => ({ ...s, color: stageColor(s) })); state.maxRain = MAXR
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
      { m: Math.min(40, LD * 0.2), head: `캠핑장 ${hDir}쪽 산으로 가세요`, caution: () => '강·계곡 쪽으로 가지 마세요' },
      { m: Math.round(LD * 0.55), head: '비탈을 따라 위로 올라가세요', caution: () => '정해진 길은 확인 전이에요. 오를 수 있는 곳으로 올라가세요' },
      { m: LD, head: '높은 곳에 도착했습니다', sub: `캠핑장보다 약 ${hillGain}m 높은 곳 · 119에 위치를 알리세요` },
    ],
  }
  const DEST = { base: shelterName, detour: '산 위 높은 곳' }

  function setRoute(key) {
    routeKey = key; RT = ROUTES[key]
    showRoute('base', key === 'base'); showRoute('detour', key === 'detour')
    detourGroup.show = key === 'detour' && !noRoute; baseGroup.show = key === 'base' && !noRoute
    const short = RT.L < 1000
    state.routeKey = key
    state.dest = {
      name: DEST[key],
      meta: key === 'base' ? `대피소 · ${ROUTES.base.main.name} 경유` : `지형으로 계산한 참고 방향 · 캠핑장보다 약 ${hillGain}m 높음`,
      distNum: short ? String(Math.round(RT.L / 10) * 10) : (RT.L / 1000).toFixed(2), distUnit: short ? 'm' : 'km',
      min: `도보 약 ${RT.min}분`,
      // 카카오맵 걷기 길찾기 (캠핑장 → 대피소). 산 대피는 정해진 길이 없어 연결하지 않는다
      // 형식: map.kakao.com/link/by/walk/출발이름,위도,경도/도착이름,위도,경도 (2026-11-23 열어서 확인)
      navUrl: key === 'base' && shelter ? `https://map.kakao.com/link/by/walk/${encodeURIComponent('캠핑장')},${camp[1]},${camp[0]}/${encodeURIComponent(shelterName)},${shelter[1]},${shelter[0]}` : '',
    }
    state.detourBtn = key !== 'detour' && !!ROUTES.base
    meU = 0
  }

  function setLevel(lv) {
    level = lv
    state.lv = { ...LV[lv], head: (lv === 1 && !ROUTES.base ? '높은 곳으로 피할 준비를 하세요' : LV[lv].head).replace('{dir}', hDir) }
    state.todo = [...LV[lv].todo]
    if (noRoute !== (lv >= 3)) {
      noRoute = lv >= 3; state.noRoute = noRoute
      Object.values(ROUTES).forEach(r => r.show(r.vis))
      detourGroup.show = routeKey === 'detour' && !noRoute; baseGroup.show = routeKey === 'base' && !noRoute
      if (noRoute) { stopNavWalk(); state.mode = 'overview' }
    }
    buildRiver(lv)
    syncZone()
    // 안전·주의: 대피소 경로. 경계 이상: 다리 경로는 고를 수 없고 바로 높은 곳으로
    const bridgeOff = lv >= 2 || !ROUTES.base
    state.baseDisabled = bridgeOff
    const want = bridgeOff ? 'detour' : 'base'
    if (routeKey !== want) { setRoute(want); if (state.mode === 'nav') { showStep(0); return } meTarget = 0; goHome() }
    if (state.mode === 'nav') showStep(step, true)
  }
  function applyStage(k) {
    const s = ST[k]
    state.stage = { label: s.label, color: stageColor(s), ai: false, level: s.level }   // level: 미리 보기 때 특보 칸 예시를 고를 때 씀
    if (k === curStage) return
    curStage = k; curFlood = s.flood
    setWater3d(s.flood)
    setRouteOver(s.flood > 0)
    paintBuildings(s.flood)
    if (s.level !== level || !riverPrims.length) setLevel(s.level)
  }

  /* ============ 길안내 ============ */
  let navWalk = null, camHeading = 0
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

  /* ============ 지도 돌리기 ============
     나침반 없이 지도에서 바로 돌림. 일반 지도 앱과 같은 손동작을 기본으로 두고:
     - 왼쪽 버튼 / 한 손가락 끌기: 지도 옮기기 · 휠 / 두 손가락 벌리기: 확대·축소 (Cesium 기본)
     - 오른쪽 버튼 끌기 / Ctrl+끌기 / 두 손가락 비틀기: 보고 있는 곳을 중심으로 360도 돌리기 + 기울기
     - 키보드 ← → : 돌리기 */
  const EV = Cesium.CameraEventType, KM = Cesium.KeyboardEventModifier, ssc = scene.screenSpaceCameraController
  ssc.zoomEventTypes = [EV.WHEEL, EV.PINCH]
  ssc.tiltEventTypes = [EV.RIGHT_DRAG, EV.MIDDLE_DRAG, EV.PINCH, { eventType: EV.LEFT_DRAG, modifier: KM.CTRL }]
  const noMenu = e => e.preventDefault()   // 오른쪽 끌기 때 메뉴가 뜨지 않게
  viewer.canvas.addEventListener('contextmenu', noMenu)
  cleanups.push(() => viewer.canvas.removeEventListener('contextmenu', noMenu))
  const onKey = e => {
    if (e.target.closest && e.target.closest('input, textarea, select')) return   // 강수량 막대 조작과 겹치지 않게
    if (e.key === 'ArrowLeft') { orbit(-0.2); e.preventDefault() }
    if (e.key === 'ArrowRight') { orbit(0.2); e.preventDefault() }
  }
  window.addEventListener('keydown', onKey)
  cleanups.push(() => window.removeEventListener('keydown', onKey))
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

  /* ============ 해와 그림자 ============ */
  // 해를 낮게(여름 오후 5시 반) 두면 빛이 비스듬히 들어와 능선·골짜기 굴곡이 드러난다
  viewer.clock.currentTime = Cesium.JulianDate.fromIso8601('2026-07-15T08:30:00Z')
  scene.globe.enableLighting = true
  viewer.shadows = true
  viewer.terrainShadows = Cesium.ShadowMode.ENABLED   // 산이 스스로 그림자를 드리우게
  Object.assign(scene.shadowMap, { softShadows: true, darkness: 0.5, size: 2048, maximumDistance: 6000 })
  // 앰비언트 오클루전: 골짜기·건물 틈처럼 빛이 덜 드는 곳을 살짝 어둡게. 휴대폰이 느리면 여기만 끄면 됨
  if (Cesium.PostProcessStageLibrary.isAmbientOcclusionSupported(scene)) {
    const AO = scene.postProcessStages.ambientOcclusion
    AO.enabled = true
    Object.assign(AO.uniforms, { intensity: 2.2, bias: 0.15, lengthCap: 0.3, stepSize: 1.6, frustumLength: 3000, blurStepSize: 1.2 })
  }

  /* ============ 등고선 (실제 지형으로 만든 지도라는 걸 눈으로 보여 줌) ============ */
  // 10m DEM 격자에서 직접 계산(마칭 스퀘어). 10m마다 가는 선, 50m마다 굵은 선 (지형도의 주곡선·계곡선처럼)
  const contourTimer = setTimeout(function contours() {   // 첫 화면이 먼저 뜨도록 계산은 조금 뒤에
    if (destroyed) return
    const g = fine, w = g.w, h = g.h, a = g.arr
    const lon = x => g.west + (x + 0.5) * g.dx, lat = y => g.north - (y + 0.5) * g.dy
    let lo = Infinity, hi = -Infinity; for (const z of a) if (z > -9000) { lo = Math.min(lo, z); hi = Math.max(hi, z) }
    const thin = [], bold = []
    for (let L = Math.ceil(lo / 10) * 10; L <= hi; L += 10) {
      const pt = new Map(), nb = new Map()
      const cross = (id, x0, y0, x1, y1) => {   // 칸 모서리에서 높이 L 이 지나는 점
        const z0 = a[y0 * w + x0], z1 = a[y1 * w + x1]
        if ((z0 < L) === (z1 < L)) return null
        if (!pt.has(id)) { const t = (L - z0) / (z1 - z0); pt.set(id, [lon(x0 + (x1 - x0) * t), lat(y0 + (y1 - y0) * t)]) }
        return id
      }
      const link = (p, q) => { (nb.get(p) || nb.set(p, []).get(p)).push(q); (nb.get(q) || nb.set(q, []).get(q)).push(p) }
      for (let y = 0; y < h - 1; y++) for (let x = 0; x < w - 1; x++) {
        const i = y * w + x, z0 = a[i], z1 = a[i + 1], z2 = a[i + w], z3 = a[i + w + 1]
        if (z0 < -9000 || z1 < -9000 || z2 < -9000 || z3 < -9000) continue
        if (Math.min(z0, z1, z2, z3) >= L || Math.max(z0, z1, z2, z3) < L) continue   // 이 높이선이 안 지나는 칸은 건너뜀
        const e = [cross('h' + i, x, y, x + 1, y), cross('v' + (i + 1), x + 1, y, x + 1, y + 1), cross('h' + (i + w), x, y + 1, x + 1, y + 1), cross('v' + i, x, y, x, y + 1)].filter(Boolean)
        if (e.length === 2) link(e[0], e[1])
        else if (e.length === 4) { link(e[0], e[1]); link(e[2], e[3]) }
      }
      const seen = new Set()
      const walk = s => { const line = []; let p = s, prev = null; while (p && !seen.has(p)) { seen.add(p); line.push(pt.get(p)); const n = nb.get(p).find(q => q !== prev && !seen.has(q)); prev = p; p = n } return line }
      for (const s of nb.keys()) {   // 끊긴 조각을 이어 한 줄로
        if (seen.has(s) || nb.get(s).length !== 1) continue
        const line = walk(s); if (line.length > 3) (L % 50 === 0 ? bold : thin).push(line)
      }
      for (const s of nb.keys()) {   // 닫힌 고리
        if (seen.has(s)) continue
        const line = walk(s); line.push(line[0]); if (line.length > 4) (L % 50 === 0 ? bold : thin).push(line)
      }
    }
    // 물 위에는 등고선을 그리지 않음 (종이 지형도처럼). 비가 오면 강이 최대 2.1배까지 넓어지므로 가장 넓을 때 폭 + 6m 로 자름
    const segs = riverLines.flatMap(r => {
      const hw = (r.river ? RIVER_HALF.river * RIVER_SWELL[3] : RIVER_HALF.stream) + 6
      return r.c.slice(1).map((q, k) => { const a0 = r.c[k]   // 상자 범위로 먼저 걸러서 빠르게
        return { a: a0, q, hw, x0: Math.min(a0[0], q[0]) - hw / MX, x1: Math.max(a0[0], q[0]) + hw / MX, y0: Math.min(a0[1], q[1]) - hw / MY, y1: Math.max(a0[1], q[1]) + hw / MY } })
    })
    const wet = p => segs.some(s => p[0] >= s.x0 && p[0] <= s.x1 && p[1] >= s.y0 && p[1] <= s.y1 && segDist(p, s.a, s.q) < s.hw)
    // 평지(마을·논밭)는 등고선이 건물 사이 엉뚱한 줄처럼 보임 → 경사 10% 이상인 비탈에만 그림
    const slope = p => { const e = 10 / MX, n = 10 / MY
      return Math.hypot(heightAt(p[0] + e, p[1]) - heightAt(p[0] - e, p[1]), heightAt(p[0], p[1] + n) - heightAt(p[0], p[1] - n)) / 20 }
    const dry = ls => ls.flatMap(l => { const out = []; let cur = []
      l.forEach(p => { if (wet(p) || slope(p) < 0.10) { if (cur.length > 3) out.push(cur); cur = [] } else cur.push(p) })
      if (cur.length > 3) out.push(cur); return out })
    // 땅에 붙이는 선(GroundPolyline)은 500줄이 넘으면 너무 느려서, DEM 높이 + 1.5m 에 띄운 일반 선으로 그림
    // 멀리서 보면 선이 뭉쳐 하얀 덩어리가 되므로 카메라가 3km 안일 때만 보임
    const add = (ls, width, col) => ls.length && scene.primitives.add(new Cesium.Primitive({
      geometryInstances: ls.map(l => new Cesium.GeometryInstance({ geometry: new Cesium.PolylineGeometry({ width, arcType: Cesium.ArcType.NONE,
        positions: l.filter((_, i) => i % 2 === 0 || i === l.length - 1).map(p => P3(p[0], p[1], 1.5)), vertexFormat: Cesium.PolylineColorAppearance.VERTEX_FORMAT }),
        attributes: { color: Cesium.ColorGeometryInstanceAttribute.fromColor(col), distanceDisplayCondition: new Cesium.DistanceDisplayConditionGeometryInstanceAttribute(0, 3000) } })),
      appearance: new Cesium.PolylineColorAppearance({ translucent: true }), asynchronous: true }))
    add(dry(thin), 1, C('--color-contour', 0.28))
    add(dry(bold), 2, C('--color-contour', 0.6))
  }, 1500)
  cleanups.push(() => clearTimeout(contourTimer))

  /* ============ 매 프레임 ============ */
  // 기울기 제한: 지평선 쪽으로 너무 눕히면 한 번 끌 때 수백 m씩 튀므로, 화면 가운데 지점을 중심으로 -20°까지만
  const MIN_TILT = Cesium.Math.toRadians(-20)
  cleanups.push(scene.preRender.addEventListener(() => {
    if (navWalk) return
    if (camera.pitch <= MIN_TILT) return
    const ray = camera.getPickRay(new Cesium.Cartesian2(scene.canvas.clientWidth / 2, scene.canvas.clientHeight / 2))
    const t = ray && scene.globe.pick(ray, scene)
    if (!t) { camera.setView({ orientation: { heading: camera.heading, pitch: MIN_TILT, roll: 0 } }); return }
    camera.lookAt(t, new Cesium.HeadingPitchRange(camera.heading, MIN_TILT, Cesium.Cartesian3.distance(camera.positionWC, t)))
    camera.lookAtTransform(Cesium.Matrix4.IDENTITY)
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
    // 물이 천천히 차오르거나 빠짐 + 잔물결
    stepWater3d(dt, now / 1000)
    if (navWalk) {
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
    moving = (navWalk ? navWalk.phase === 'walk' : Math.abs(meTarget - meU) * RT.L > 2)
    mePos = P3(mp[0], mp[1], 0.5)
    meTag.p = mePos
    // 화면에서 오른쪽으로 가면 좌우 반전된 그림 (얼굴이 가는 쪽을 보게)
    if (moving) {
      const ahead = RT.at(Math.min(RT.L, meU * RT.L + 8)), back = RT.at(Math.max(0, meU * RT.L - 8))
      const a = scene.cartesianToCanvasCoordinates(P3(ahead[0], ahead[1])), b = scene.cartesianToCanvasCoordinates(P3(back[0], back[1]))
      if (a && b && Math.abs(a.x - b.x) > 1.5) faceRight = a.x > b.x
    }
  }))
  cleanups.push(scene.postRender.addEventListener(placeTags))

  // 지도를 만지면: 시트를 접고(지도가 가려지지 않게), 길안내 걷기를 멈춤
  const onCanvasDown = () => {
    state.sheetOpen = false
    if (navWalk) { camera.cancelFlight(); stopNavWalk() }
  }
  viewer.canvas.addEventListener('pointerdown', onCanvasDown)

  /* ============ 비 효과 (화면 위 얇은 빗줄기) ============ */
  // 막대(3시간 강수량)에 따라 빗줄기 수만 달라지게, 지도가 가려지지 않도록 옅게 그린다
  // 5mm 미만은 없음 → 60mm 쯤 눈에 띄게 → 최대(160mm)에서 줄기 170개 정도. 움직임 줄이기 설정이면 그리지 않는다
  // 2026-11-23 처음 값(최대 90개·투명도 0.22)이 너무 옅다고 해서 진하게 함
  const rainFx = (() => {
    const cv = document.createElement('canvas')
    cv.setAttribute('aria-hidden', 'true')
    cv.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;pointer-events:none'
    container.appendChild(cv)
    const g = cv.getContext('2d'), color = token('--color-dm-text')
    let drops = [], want = 0, raf = 0, last = 0
    const fit = () => { const r = devicePixelRatio || 1; cv.width = cv.clientWidth * r; cv.height = cv.clientHeight * r; g.setTransform(r, 0, 0, r, 0, 0) }
    const drop = (top) => ({ x: Math.random() * (cv.clientWidth + 60), y: top ? -20 : Math.random() * cv.clientHeight, v: 380 + Math.random() * 220, l: 14 + Math.random() * 12 })
    function frame(t) {
      const dt = Math.min(0.05, (t - (last || t)) / 1000); last = t
      const w = cv.clientWidth, h = cv.clientHeight
      g.clearRect(0, 0, w, h)
      while (drops.length < want) drops.push(drop(false))
      if (drops.length > want) drops.length = want
      g.strokeStyle = color; g.lineWidth = 1.3; g.globalAlpha = 0.38; g.beginPath()
      for (const d of drops) {
        d.y += d.v * dt; d.x -= d.v * dt * 0.18   // 살짝 비스듬히
        if (d.y > h + 20) Object.assign(d, drop(true))
        g.moveTo(d.x, d.y); g.lineTo(d.x + d.l * 0.18, d.y - d.l)
      }
      g.stroke()
      raf = want ? requestAnimationFrame(frame) : 0
      if (!want) g.clearRect(0, 0, w, h)
    }
    const ro = new ResizeObserver(fit); ro.observe(cv); fit()
    cleanups.push(() => { cancelAnimationFrame(raf); ro.disconnect(); cv.remove() })
    return mm => {
      want = reduce || mm < 5 ? 0 : Math.round(30 + 140 * Math.min(1, (mm - 5) / (MAXR - 5)))
      if (want && !raf) { last = 0; raf = requestAnimationFrame(frame) }
    }
  })()

  /* ============ 시작 ============ */
  setRoute(routeKey)
  buildRiver(1)
  // 처음에는 미리 보기 60mm(준비)로 보여 주고, 화면(DisasterMapView)이 서버 실황을 받으면 setLive 로 바꾼다
  applyStage(stageFromTable(state.rain))
  rainFx(state.rain)
  {
    const other = shelter || hEnd, cx = (camp[0] + other[0]) / 2, cy = (camp[1] + other[1]) / 2
    camera.setView({ destination: Cesium.Cartesian3.fromDegrees(cx, cy - 0.03, 4000), orientation: { heading: 0, pitch: Cesium.Math.toRadians(-45) } })
  }
  goHome()
  // 처음 보이는 타일이 들어오면 바로 로딩 화면을 걷는다
  // 예전에는 최소 2.5초를 일부러 기다렸는데, 그만큼 사용자가 빈 화면을 봐야 해서 없앴다
  // 바깥 지도 서버가 느려도 3초가 지나면 걷는다 (나머지 타일은 지도를 보는 동안 채워짐)
  {
    const t0 = performance.now()
    const timer = setInterval(() => {
      if (!scene.globe.tilesLoaded && performance.now() - t0 < 3000) return
      clearInterval(timer); state.loading = false
    }, 150)
    cleanups.push(() => clearInterval(timer))
  }

  /* ============ 화면(Vue)이 부르는 동작 ============ */
  return {
    // 미리 보기: 막대 값(3시간 강수량)으로 단계를 정한다
    setRain(mm) { state.rain = mm; state.live.on = false; applyStage(stageFromTable(mm)); syncZone(); rainFx(mm) },
    // 실시간: 단계는 서버가 3·12·24시간 기준으로 정한 level 을 그대로 쓰고, 슬라이더에는 3시간 비만 보여 준다
    setLive(level, mm) {
      const k = ST.findIndex(s => s.level === level)
      if (k < 0) return
      state.rain = Math.min(Math.round(mm), MAXR); state.live.on = true
      applyStage(k); syncZone(); rainFx(state.rain)
    },
    selectRoute(key) { if (key === 'base' && state.baseDisabled) return; setRoute(key); meTarget = 0; goHome() },
    where() { state.sheetOpen = false; fly(routeKey === 'base' ? shelter : hEnd, camera.heading, -35, 520) },
    startNav() { meU = meTarget = 0; state.mode = 'nav'; state.sheetOpen = true; showStep(0) },
    prev() { showStep(step - 1) },
    next() { if (step === STEPS[routeKey].length - 1) this.endNav(); else showStep(step + 1) },
    endNav() { stopNavWalk(); state.mode = 'overview'; meTarget = 0; state.detourBtn = routeKey !== 'detour' && !!ROUTES.base; goHome() },
    detour() { setRoute('detour'); meU = 0; showStep(0) },
    destroy() {
      if (destroyed) return; destroyed = true
      cleanups.forEach(fn => fn())
      viewer.canvas.removeEventListener('pointerdown', onCanvasDown)
      tags.forEach(t => t.el.remove())
      viewer.destroy()
    },
  }
}
