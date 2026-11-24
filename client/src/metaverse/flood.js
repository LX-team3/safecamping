// 돌발 미션 "비와 침수 대피"
// 비가 오기 시작하면 강물이 먼저 차오르고, 그다음 캠핑장이 점점 물에 잠겨요.
// 강물은 캠핑장의 북쪽과 동쪽 테두리 전체에서 동시에 넘쳐 들어와요.
// 반대편 개울도 넘치지만 개울 둘레 몇 m까지만 잠기고 멈춰요.
// 물에 잠기기 전에 출구 고리까지 도망가면 성공이에요.
//
// 주의: 여기 나오는 시간과 속도는 게임을 위한 값이에요. 실제 홍수가 차오르는 속도가 아니에요.
import * as THREE from 'three'
import { pointInRing, distToSegs } from './geo.js'
import { disposeTree } from './shapes.js'
import { emojiTexture } from './missions.js'

// ---------- 설정 (게임용 값) ----------
const RIVER_FILL_SECONDS = 8   // 강물이 둑 높이까지 차오르는 데 걸리는 시간(초)
const CAMP_FLOOD_SECONDS = 45  // 그 뒤 캠핑장 전체가 잠기는 데 걸리는 시간(초)
const CELL = 1.5               // 물을 그리는 칸 크기(m). 작을수록 물 가장자리가 매끄럽지만 느려져요
const WATER_START = 0.12       // 물이 처음 땅 위로 올라온 높이(m)
const WATER_END = 0.55         // 다 잠겼을 때 물 높이(m). 거북이 다리가 잠기는 정도
const SAFE_MARGIN = 2          // 물 가장자리에서 이만큼(m)은 봐줘요 (닿자마자 실패하지 않게)
const EXIT_DIST = 4            // 출구 고리에 이 거리(m) 안으로 들어가면 성공
const INLET_FACING = 0.3       // 테두리 변이 북쪽·동쪽을 얼마나 바라봐야 물이 넘어오는 변으로 볼지 (클수록 까다로워요)
const STREAM_REACH = 8         // 개울이 넘쳐서 번지는 가장 먼 거리(m). 여기까지만 잠기고 멈춰요
const STREAM_UNTIL = 10        // 강물이 이만큼(m) 들어올 동안 개울 둘레가 다 번져요
const EXIT_KEEP = 40          // 출구에서 이 거리(m) 안은 늦게 잠겨요 (출구가 강 옆이어도 도망갈 수 있게)
const EXIT_KEEP_POWER = 5      // 그 안에서 얼마나 더 늦게 잠기게 할지. 클수록 출구 둘레가 오래 남아요
const RAIN_DROPS = 1400        // 빗방울 수
const RAIN_BOX = { w: 70, h: 40 } // 캐릭터 둘레에 비가 내리는 범위(m)

// camp: buildCampsite가 돌려준 값 (ring 캠핑장 테두리, bedRings 강, streamLines 개울, exit 출구 점)
export function createFlood({ scene, camp, terrain }) {
  const floor = terrain.floor
  const group = new THREE.Group()
  group.visible = false
  scene.add(group)

  // 잠기는 순서 값: 작을수록 먼저 잠겨요. 물은 두 군데에서 들어와요.
  // 1) 강: 캠핑장 테두리 가운데 북쪽과 동쪽을 바라보는 변 전체에서 동시에 넘쳐 들어와요.
  //    그 변에서 가까운 곳부터 잠기고, 끝내 캠핑장 전체가 잠겨요
  // 2) 개울: 반대편 개울(stream)이 넘치지만 개울에서 STREAM_REACH m까지만 번지고 거기서 멈춰요
  // 개울 자료가 없으면 강 쪽에서만 들어와요.
  // (어디로 들어오는지는 게임에서 정한 설정이에요. 실제 홍수 자료로 구한 것이 아니에요)
  //
  // 북쪽·동쪽을 바라보는 변 찾기: 변마다 "바깥쪽 방향"을 구해요.
  // 동쪽은 x가 커지는 쪽, 북쪽은 z가 작아지는 쪽이라서 (바깥 x - 바깥 z)가 크면 북쪽이나 동쪽을 보는 변이에요
  const R = camp.ring
  let area = 0 // 테두리가 도는 방향(시계/반시계)을 알아내려고 넓이를 부호와 함께 구해요
  for (let i = 0; i < R.length; i++) {
    const a = R[i], b = R[(i + 1) % R.length]
    area += a[0] * b[1] - b[0] * a[1]
  }
  const inletEdges = [] // 물이 넘어오는 변들
  for (let i = 0; i < R.length; i++) {
    const a = R[i], b = R[(i + 1) % R.length]
    const len = Math.hypot(b[0] - a[0], b[1] - a[1])
    if (len < 0.01) continue
    const s = area > 0 ? 1 : -1
    const nx = (s * (b[1] - a[1])) / len  // 바깥쪽 방향의 x (동쪽이 +)
    const nz = (-s * (b[0] - a[0])) / len // 바깥쪽 방향의 z (북쪽이 -)
    if (nx - nz > INLET_FACING) inletEdges.push([a, b])
  }
  if (!inletEdges.length) for (let i = 0; i < R.length; i++) inletEdges.push([R[i], R[(i + 1) % R.length]]) // 못 찾으면 테두리 전체

  const streams = camp.streamLines || []
  function keyAt(x, z) {
    let d = Infinity
    for (const e of inletEdges) d = Math.min(d, distToSegs(x, z, e, false))
    // 개울 둘레는 강물이 STREAM_UNTIL m 들어오는 동안 다 번지고, 그 뒤로는 더 넓어지지 않아요
    for (const line of streams) {
      const ds = distToSegs(x, z, line, false)
      if (ds < STREAM_REACH) d = Math.min(d, (ds / STREAM_REACH) * STREAM_UNTIL)
    }
    // 출구가 강 가까이에 있어도 대피할 수 있게, 출구 둘레(EXIT_KEEP m)는 가장 늦게 잠기게 해요.
    // 출구에 가까울수록 더 늦게 잠겨서, 출구 자리가 맨 마지막이에요
    if (camp.exit) {
      const de = Math.hypot(x - camp.exit[0], z - camp.exit[1])
      if (de < EXIT_KEEP) d += (EXIT_KEEP - de) * EXIT_KEEP_POWER
    }
    return d
  }

  // ---------- 캠핑장 물 ----------
  // 캠핑장을 작은 칸으로 나누고, 먼저 잠기는 순서대로 줄을 세워요.
  // 그러면 "앞에서부터 n칸만 그리기"로 물이 퍼져 나가는 모습을 만들 수 있어요
  let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity
  for (const [x, z] of camp.ring) {
    minX = Math.min(minX, x); maxX = Math.max(maxX, x)
    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z)
  }
  const cells = []
  for (let x = minX; x < maxX; x += CELL) {
    for (let z = minZ; z < maxZ; z += CELL) {
      const cx = x + CELL / 2
      const cz = z + CELL / 2
      if (pointInRing(cx, cz, camp.ring)) cells.push({ x: cx, z: cz, key: keyAt(cx, cz) })
    }
  }
  cells.sort((a, b) => a.key - b.key)

  const pos = new Float32Array(cells.length * 12)
  const idx = new Uint32Array(cells.length * 6)
  const h = CELL / 2
  cells.forEach((c, i) => {
    pos.set([c.x - h, 0, c.z - h, c.x + h, 0, c.z - h, c.x + h, 0, c.z + h, c.x - h, 0, c.z + h], i * 12)
    const v = i * 4
    idx.set([v, v + 2, v + 1, v, v + 3, v + 2], i * 6)
  })
  const waterGeo = new THREE.BufferGeometry()
  waterGeo.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  waterGeo.setIndex(new THREE.BufferAttribute(idx, 1))
  waterGeo.computeVertexNormals()
  const waterMat = new THREE.MeshStandardMaterial({
    color: 0x6d8fa8, transparent: true, opacity: 0.78, roughness: 0.3, side: THREE.DoubleSide,
    polygonOffset: true, polygonOffsetFactor: -6, polygonOffsetUnits: -6, // 바닥·도로보다 앞에 그려서 깜빡이지 않게
  })
  const campWater = new THREE.Mesh(waterGeo, waterMat)
  campWater.frustumCulled = false
  group.add(campWater)

  // ---------- 강물 ----------
  // 하천 전체 모양으로 물 면을 만들고, 강바닥 높이에서 캠핑장 땅 높이까지 올려요
  const riverLow = (terrain.bedLevel ?? floor - 3) + 0.3
  const riverWaters = camp.bedRings.map((ring) => {
    const shape = new THREE.Shape()
    ring.forEach(([x, z], i) => (i ? shape.lineTo(x, -z) : shape.moveTo(x, -z)))
    const geo = new THREE.ShapeGeometry(shape)
    geo.rotateX(-Math.PI / 2)
    const m = new THREE.Mesh(geo, waterMat)
    group.add(m)
    return m
  })

  // ---------- 출구 ----------
  // QGIS에서 찍은 출구 점이 있으면 그곳, 없으면 가장 늦게 잠기는 칸을 임시 출구로 써요
  const last = cells[cells.length - 1]
  let exitXZ = { x: camp.center[0], z: camp.center[1] }
  if (camp.exit) {
    exitXZ = { x: camp.exit[0], z: camp.exit[1] }
  } else if (last) {
    // 가장 늦게 잠기는 칸은 보통 테두리에 딱 붙어 있어요. 고리에 들어가기 쉽게 가운데 쪽으로 5m 당겨요
    const dx = camp.center[0] - last.x
    const dz = camp.center[1] - last.z
    const l = Math.hypot(dx, dz) || 1
    exitXZ = { x: last.x + (dx / l) * 5, z: last.z + (dz / l) * 5 }
  }
  const exitPos = new THREE.Vector3(exitXZ.x, terrain.heightAt(exitXZ.x, exitXZ.z), exitXZ.z)

  const ringGeo = new THREE.RingGeometry(3.2, 4, 40)
  ringGeo.rotateX(-Math.PI / 2)
  const ring = new THREE.Mesh(ringGeo, new THREE.MeshBasicMaterial({
    color: 0x3ddc84, transparent: true, opacity: 0.9, depthTest: false, // 물에 잠겨도 고리는 보이게
  }))
  ring.renderOrder = 5
  ring.position.set(exitPos.x, exitPos.y + 0.7, exitPos.z)
  const icon = new THREE.Sprite(new THREE.SpriteMaterial({ map: emojiTexture('🚪'), depthTest: false }))
  icon.renderOrder = 6
  icon.scale.set(4, 4, 1)
  icon.position.set(exitPos.x, exitPos.y + 6, exitPos.z)
  // 캐릭터 머리 위 화살표 (출구를 가리켜요)
  const arrow = new THREE.Group()
  const cone = new THREE.Mesh(new THREE.ConeGeometry(0.4, 1.2, 4), new THREE.MeshBasicMaterial({ color: 0x3ddc84 }))
  cone.rotation.x = Math.PI / 2
  arrow.add(cone)
  group.add(ring, icon, arrow)

  // ---------- 비 ----------
  // 짧은 선 여러 개를 캐릭터 둘레에 뿌리고, 아래로 떨어뜨렸다가 바닥에 닿으면 다시 위로 올려요
  const rainPos = new Float32Array(RAIN_DROPS * 6)
  const drops = []
  for (let i = 0; i < RAIN_DROPS; i++) {
    drops.push({
      x: (Math.random() - 0.5) * RAIN_BOX.w,
      y: Math.random() * RAIN_BOX.h,
      z: (Math.random() - 0.5) * RAIN_BOX.w,
      v: 28 + Math.random() * 14, // 떨어지는 빠르기
    })
  }
  const rainGeo = new THREE.BufferGeometry()
  rainGeo.setAttribute('position', new THREE.BufferAttribute(rainPos, 3))
  const rain = new THREE.LineSegments(rainGeo, new THREE.LineBasicMaterial({ color: 0xd6e6f2, transparent: true, opacity: 0.55 }))
  rain.frustumCulled = false
  group.add(rain)

  function updateRain(dt, center) {
    for (let i = 0; i < RAIN_DROPS; i++) {
      const d = drops[i]
      d.y -= d.v * dt
      if (d.y < 0) d.y += RAIN_BOX.h
      const x = center.x + d.x
      const y = center.y + d.y
      const z = center.z + d.z
      rainPos.set([x, y, z, x + 0.15, y - 1.1, z], i * 6) // 살짝 비스듬한 빗줄기
    }
    rainGeo.attributes.position.needsUpdate = true
  }

  // ---------- 진행 ----------
  let active = false
  let time = 0       // 비가 오기 시작한 뒤 흐른 시간(초). 결과 창이 떠 있는 동안에는 멈춰요
  let shown = 0      // 지금 그리고 있는 칸 수
  let anim = 0

  function setWater(n, level) {
    shown = n
    waterGeo.setDrawRange(0, n * 6)
    campWater.position.y = floor + level
    campWater.visible = n > 0
  }

  // 비 시작
  function start() {
    active = true
    time = 0
    group.visible = true
    setWater(0, WATER_START)
    for (const m of riverWaters) m.position.y = riverLow
  }

  // 끝내기: 물과 비를 치워요
  function stop() {
    active = false
    group.visible = false
  }

  // 매 프레임. playerPos가 있으면 시간이 흐르고, 없으면(결과 창이 떠 있을 때) 멈춰 있어요.
  // camFocus: 비를 내릴 가운데 (캐릭터 위치)
  // 돌려주는 값: 'safe' 출구 도착 / 'drowned' 물에 잠김 / null 아직 진행 중
  function update(dt, playerPos, camFocus) {
    if (!active) return null
    anim += dt
    if (camFocus) updateRain(dt, camFocus)
    icon.position.y = exitPos.y + 6 + Math.sin(anim * 2) * 0.4
    ring.scale.setScalar(1 + Math.sin(anim * 4) * 0.08)
    if (!playerPos) {
      arrow.visible = false
      return null
    }
    time += dt

    // 1단계: 강물이 차올라요
    const fill = Math.min(1, time / RIVER_FILL_SECONDS)
    const riverY = riverLow + (floor + WATER_START - riverLow) * fill
    // 2단계: 캠핑장이 잠겨요 (0 → 1)
    const p = Math.min(1, Math.max(0, (time - RIVER_FILL_SECONDS) / CAMP_FLOOD_SECONDS))
    const level = WATER_START + (WATER_END - WATER_START) * p
    for (const m of riverWaters) m.position.y = fill < 1 ? riverY : floor + level
    setWater(Math.floor(cells.length * p), level)

    // 화살표: 출구 쪽을 가리켜요
    arrow.visible = true
    arrow.position.set(playerPos.x, playerPos.y + 2.6, playerPos.z)
    arrow.rotation.y = Math.atan2(exitPos.x - playerPos.x, exitPos.z - playerPos.z)

    // 출구에 닿았는지
    if (Math.hypot(exitPos.x - playerPos.x, exitPos.z - playerPos.z) < EXIT_DIST) return 'safe'

    // 물에 잠겼는지: 지금 물 가장자리의 순서 값보다 캐릭터 자리의 순서 값이 작으면 잠긴 거예요
    if (shown > 0) {
      const edge = cells[shown - 1].key
      if (keyAt(playerPos.x, playerPos.z) < edge - SAFE_MARGIN) return 'drowned'
    }
    return null
  }

  function dispose() {
    scene.remove(group)
    disposeTree(group)
  }

  return {
    get active() { return active },
    exitPos,
    start, stop, update, dispose,
  }
}
