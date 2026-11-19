// 데크·카라반·주차칸: 모양 만들기, 도로에 붙이기, 부딪힘(충돌) 상자
import * as THREE from 'three'
import { inShape } from './geo.js'
import { mat, box, extrude, signTexture } from './shapes.js'

// 데크·카라반 간격 비율. 1이면 선 끝에서 끝까지, 작을수록 촘촘해져요 (예: 0.8)
export const ROW_SHRINK = 0.85

// 도로에 붙이기: 도로에서 SNAP_MAX(m) 안에 있는 데크·카라반을 도로 가장자리로 당겨요
const SNAP_MAX = 25 // 이보다 멀리 있는 건 그대로 둬요
const SNAP_GAP = 0.5 // 도로 가장자리와 데크 사이 틈(m)

// ---------- 시설 모양 ----------
const TENT_COLORS = [0xe86a4a, 0x3d8bd9, 0xf2c14e, 0x6bbf59]

export function makeDeck(i = 0) {
  const g = new THREE.Group()
  g.add(box(7, 0.2, 7, 0xcfc8b8))                        // 자갈 바닥
  g.add(box(1.8, 0.1, 0.8, 0x8a5a33, -1.5, 0.7, 1.8))    // 테이블 상판
  g.add(box(0.2, 0.7, 0.6, 0x6b4426, -1.5, 0, 1.8))      // 테이블 다리
  const tent = new THREE.Mesh(new THREE.ConeGeometry(1.8, 2.2, 4), mat(TENT_COLORS[i % 4]))
  tent.position.set(1.2, 0.2 + 1.1, -1.2)
  tent.rotation.y = Math.PI / 4
  g.add(tent)
  return g
}

// 카라반: 현장 사진을 보고 만든 모양이에요
// (밝은 회색 판넬 네모 몸체 + 짙은 큰 창 + 옆 나무 데크와 검은 파고라 + 빨간 번호판)
// 방향: 몸체는 z쪽으로 길고(7m), 데크는 +x쪽 옆에 붙어요. 숫자를 바꾸면 크기·색이 바뀌어요
const CARAVAN = {
  w: 2.6, h: 2.5, len: 7,   // 몸체 가로, 높이, 길이 (m)
  lift: 0.3,                // 몸체가 땅에서 떠 있는 높이 (m)
  wall: 0xdcd9d2,           // 벽 판넬 (밝은 회색)
  band: 0xb3a999,           // 가운데 띠 (회갈색)
  glass: 0x22313b,          // 창문 (짙은 유리)
  door: 0x6f7672,           // 문
  wood: 0x8a5a3c,           // 데크 나무
  frame: 0x1f1f1f,          // 파고라 (검은 철 기둥과 지붕)
  deckW: 3.5, deckLen: 3.6, // 옆 데크 가로, 길이 (m)
}

// 의자 하나 (dx, dz: 등받이가 붙는 쪽)
function makeChair(x, y, z, dx, dz) {
  const g = new THREE.Group()
  g.add(box(0.45, 0.45, 0.45, 0xb07a45, x, y, z))                                                // 앉는 곳
  g.add(box(dx ? 0.08 : 0.45, 0.45, dz ? 0.08 : 0.45, 0xb07a45, x + dx * 0.2, y + 0.45, z + dz * 0.2)) // 등받이
  return g
}

// label: 번호판 글자 (예: "F-12"). 없으면 번호판을 달지 않아요
export function makeCaravan(label) {
  const C = CARAVAN
  const g = new THREE.Group()
  const top = C.lift + C.h      // 지붕 높이
  const side = C.w / 2          // 옆벽 위치 (+x쪽이 데크 쪽)
  const end = C.len / 2         // 짧은 끝벽 위치

  // 몸체
  g.add(box(C.w - 0.3, C.lift, C.len - 0.3, 0x4a4844))                    // 아래 받침 (어두운 색)
  g.add(box(C.w, C.h, C.len, C.wall, 0, C.lift))                          // 벽
  g.add(box(C.w + 0.04, 0.75, C.len + 0.04, C.band, 0, C.lift + 0.85))    // 가운데 띠
  g.add(box(C.w + 0.1, 0.08, C.len + 0.1, 0xbdb9b0, 0, top))              // 납작한 지붕

  // 데크 쪽 벽: 문과 큰 창
  g.add(box(0.06, 1.95, 0.9, C.door, side, C.lift + 0.05, 0.9))           // 문
  g.add(box(0.07, 0.5, 0.3, 0xcfd8dc, side, C.lift + 1.25, 0.9))          // 문에 달린 작은 창
  g.add(box(0.06, 0.95, 2.2, C.glass, side, C.lift + 0.8, -1.6))          // 큰 창
  // 반대쪽 벽과 끝벽 창
  g.add(box(0.06, 0.6, 1.4, C.glass, -side, C.lift + 1.1, 1.2))
  g.add(box(0.06, 0.6, 1.4, C.glass, -side, C.lift + 1.1, -1.4))
  g.add(box(1.3, 0.95, 0.06, C.glass, 0.4, C.lift + 0.8, -end))           // 큰 창이 모서리를 돌아 이어져요

  // 에어컨 실외기 (끝벽 앞)
  g.add(box(0.9, 0.6, 0.35, 0xf2f2f2, -0.6, 0.1, end + 0.25))
  g.add(box(0.5, 0.45, 0.02, 0x9aa0a4, -0.6, 0.18, end + 0.43))

  // 옆 데크 + 파고라
  const dx = side + C.deckW / 2 // 데크 가운데 x
  const hw = C.deckW / 2 - 0.15
  const hl = C.deckLen / 2 - 0.15
  g.add(box(C.deckW, 0.2, C.deckLen, C.wood, dx, 0))                      // 나무 바닥
  for (const [px, pz] of [[-hw, -hl], [hw, -hl], [-hw, hl], [hw, hl]]) {
    g.add(box(0.12, 2.3, 0.12, C.frame, dx + px, 0.2, pz))                // 기둥 4개
  }
  g.add(box(C.deckW + 0.2, 0.1, C.deckLen + 0.2, C.frame, dx, 2.5))       // 지붕

  // 테이블과 의자 4개
  const tx = dx + 0.2
  g.add(box(1, 0.06, 1, 0x9a6b3f, tx, 0.9, 0))                            // 상판
  g.add(box(0.12, 0.7, 0.12, C.frame, tx, 0.2, 0))                        // 다리
  g.add(makeChair(tx - 0.85, 0.2, 0, -1, 0), makeChair(tx + 0.85, 0.2, 0, 1, 0))
  g.add(makeChair(tx, 0.2, -0.85, 0, -1), makeChair(tx, 0.2, 0.85, 0, 1))

  // 번호판: 어느 끝이 도로 쪽이 될지 몰라서 양쪽 끝벽에 하나씩 달아요
  if (label) {
    const tex = signTexture([{ text: label, x: 0.5, size: 84, color: '#d0232a' }], { bg: '#ecebe6', w: 256, h: 128 })
    const signMat = new THREE.MeshStandardMaterial({ map: tex })
    for (const dir of [1, -1]) {
      const sign = new THREE.Mesh(new THREE.PlaneGeometry(0.9, 0.45), signMat)
      sign.position.set(dir * 0.7, top - 0.35, dir * (end + 0.02))
      if (dir < 0) sign.rotation.y = Math.PI // 반대쪽 벽은 뒤돌려서 붙여요
      g.add(sign)
    }
  }
  return g
}

// 주차칸: 바닥(잔디 +0.1m, 바닥 영역 +0.17m)보다 확실히 위에 그려야 보여요
const PARK_Y = 0.3
export function makeParking() {
  const g = new THREE.Group()
  g.add(box(2.5, 0.03, 5, 0x5c6166, 0, PARK_Y))           // 칸 바닥
  g.add(box(0.15, 0.04, 5, 0xffffff, -1.25, PARK_Y + 0.02)) // 왼쪽 흰 선
  g.add(box(0.15, 0.04, 5, 0xffffff, 1.25, PARK_Y + 0.02))  // 오른쪽 흰 선
  g.add(box(2.5, 0.04, 0.15, 0xffffff, 0, PARK_Y + 0.02, -2.5)) // 안쪽 끝 선
  return g
}

// ---------- 점으로 찍은 시설 (facilities 레이어, 예전 방식) ----------
// 건물은 buildings 레이어(폴리곤)로 세우기 때문에, 여기에는 건물이 아닌 것만 남겼어요
export const FACILITY = {
  fountain: () => {
    const g = new THREE.Group()
    const base = new THREE.Mesh(new THREE.CylinderGeometry(7, 7, 0.3, 24), mat(0x9a9a9a))
    base.position.y = 0.15
    const water = new THREE.Mesh(new THREE.CylinderGeometry(5.5, 5.5, 0.32, 24), mat(0x6ec3ea))
    water.position.y = 0.17
    g.add(base, water)
    return g
  },
  deck: () => makeDeck(1),
}

// ---------- 도로에 붙이기 ----------
// 가장 가까운 도로 가장자리(테두리 선분) 찾기
// - 붙는 위치: 진짜로 가장 가까운 변
// - 방향: 그 변이 짧으면(8m 미만, 모서리를 깎은 부분) 같은 테두리에서 가장 가까운 긴 변의 방향
function nearestRoadEdge(px, pz, polys) {
  let best = { d: Infinity, qx: px, qz: pz, angle: 0, len: 0, ring: null }
  for (const pts of polys) {
    for (let i = 0; i < pts.length; i++) {
      const [ax, az] = pts[i]
      const [bx, bz] = pts[(i + 1) % pts.length]
      const dx = bx - ax
      const dz = bz - az
      const len2 = dx * dx + dz * dz
      if (len2 < 0.25) continue
      const t = Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len2))
      const qx = ax + t * dx
      const qz = az + t * dz
      const d = Math.hypot(px - qx, pz - qz)
      if (d < best.d) best = { d, qx, qz, angle: Math.atan2(-dz, dx), len: Math.sqrt(len2), ring: pts }
    }
  }
  if (best.ring && best.len < 8) {
    let bestLong = Infinity
    const pts = best.ring
    for (let i = 0; i < pts.length; i++) {
      const [ax, az] = pts[i]
      const [bx, bz] = pts[(i + 1) % pts.length]
      const dx = bx - ax
      const dz = bz - az
      const len2 = dx * dx + dz * dz
      if (len2 < 64) continue
      const t = Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len2))
      const d = Math.hypot(px - ax - t * dx, pz - az - t * dz)
      if (d < bestLong) { bestLong = d; best.angle = Math.atan2(-dz, dx) }
    }
  }
  return best
}

// 같은 줄 데크·카라반 중 혼자 많이 돌아간 것을 이웃에 맞춰 바로잡아요
// period: 데크는 네모라 90도마다 같은 모양, 카라반은 180도마다 같은 모양
export function straightenRow(objs, period, maxDiffDeg = 12) {
  const wrap = (a) => ((a % period) + period * 1.5) % period - period / 2 // -period/2 ~ period/2
  const fixed = objs.map((o) => o.rotation.y)
  for (let i = 0; i < objs.length; i++) {
    const nb = [objs[i - 1], objs[i + 1]].filter(Boolean)
    if (!nb.length) continue
    // 이웃 각도 평균 (같은 모양 주기를 고려해서)
    const base = nb[0].rotation.y
    const avg = base + nb.reduce((sum, o) => sum + wrap(o.rotation.y - base), 0) / nb.length
    const diff = wrap(objs[i].rotation.y - avg)
    if (Math.abs(diff) > (maxDiffDeg * Math.PI) / 180) fixed[i] = objs[i].rotation.y - diff
  }
  objs.forEach((o, i) => { o.rotation.y = fixed[i] })
}

// 물체를 가장 가까운 도로 가장자리에 붙이고, 도로와 나란하게 돌려요
// half: 도로 쪽을 향한 물체 크기의 절반(m)
// roadPolys: 도로 모양 목록 [{ outer, holes }]
// fallbackAngle: 도로가 하나도 없을 때 쓸 방향
export function snapToRoad(obj, x, z, half, heightAt, roadPolys = [], fallbackAngle = 0) {
  if (!roadPolys.length) {
    obj.rotation.y = fallbackAngle
    obj.position.set(x, heightAt(x, z), z)
    return
  }
  // 바깥 테두리와 구멍 테두리 모두가 도로 가장자리예요
  const e = nearestRoadEdge(x, z, roadPolys.flatMap((r) => [r.outer, ...r.holes]))
  obj.rotation.y = e.angle
  if (e.d < SNAP_MAX) {
    let dx = x - e.qx
    let dz = z - e.qz
    const len = Math.hypot(dx, dz) || 1
    dx /= len
    dz /= len
    // 도로 안에 들어가 있으면 바깥쪽으로 밀어내요
    if (roadPolys.some((r) => inShape(x, z, r))) { dx = -dx; dz = -dz }
    x = e.qx + dx * (half + SNAP_GAP)
    z = e.qz + dz * (half + SNAP_GAP)
  }
  obj.position.set(x, heightAt(x, z), z)
}

// ---------- 사이트 (sites 레이어: 데크·카라반·주차칸을 하나씩 폴리곤으로 딴 것) ----------
// name 예: "B-3" → B줄 3번. 퀴즈 위치(anchor)가 이 이름을 찾아요
export function parseSiteName(name) {
  const m = String(name ?? '').trim().match(/^([A-Za-z]+)\s*-?\s*(\d+)$/)
  return m ? { row: m[1].toUpperCase(), no: Number(m[2]) } : null
}

// 폴리곤에서 가장 긴 변의 방향 (사이트가 놓인 방향)
function longestEdgeAngle(ring) {
  let best = 0
  let angle = 0
  for (let i = 0; i < ring.length; i++) {
    const [ax, az] = ring[i]
    const [bx, bz] = ring[(i + 1) % ring.length]
    const l = Math.hypot(bx - ax, bz - az)
    if (l > best) { best = l; angle = Math.atan2(-(bz - az), bx - ax) }
  }
  return angle
}

export function makeSite(ringWorld, type, idx, heightAt, label) {
  const n = ringWorld.length
  const [cx, cz] = ringWorld.reduce((s, [x, z]) => [s[0] + x / n, s[1] + z / n], [0, 0])
  const ring = ringWorld.map(([x, z]) => [x - cx, z - cz])
  const g = new THREE.Group()
  const angle = longestEdgeAngle(ring)

  if (type === 'parking') {
    // 칸 바닥 + 테두리 흰 선 (그린 모양 그대로)
    const floor = extrude(ring, 0.03, 0x5c6166)
    floor.position.y = PARK_Y
    g.add(floor)
    for (let i = 0; i < ring.length; i++) {
      const [ax, az] = ring[i]
      const [bx, bz] = ring[(i + 1) % ring.length]
      const l = Math.hypot(bx - ax, bz - az)
      if (l < 0.5) continue
      const line = box(0.15, 0.04, l, 0xffffff, (ax + bx) / 2, PARK_Y + 0.02, (az + bz) / 2)
      line.rotation.y = Math.atan2(bx - ax, bz - az)
      g.add(line)
    }
  } else {
    // 데크·카라반 바닥: 그린 모양 그대로 자갈(데크) / 짙은 데크판(카라반)
    const base = extrude(ring, 0.2, type === 'caravan' ? 0x3a3a3a : 0xcfc8b8)
    g.add(base)
    // 위에 올릴 물건은 사이트 방향에 맞춰 돌려요
    const items = type === 'caravan' ? makeCaravan(label) : makeDeckItems(idx)
    items.rotation.y = type === 'caravan' ? angle + Math.PI / 2 : angle
    g.add(items)
  }

  g.position.set(cx, heightAt(cx, cz), cz)
  g.userData.angle = angle
  return g
}

// 데크 위 물건만 (바닥은 폴리곤이 대신해요)
function makeDeckItems(i = 0) {
  const g = makeDeck(i)
  g.children.shift() // 첫 번째(자갈 바닥 7×7)를 빼요
  return g
}

// ---------- 부딪힘(충돌) 상자 ----------
// 캐릭터가 통과하지 못하는 네모 영역이에요. [가운데 x, 가운데 z, 가로 절반, 세로 절반] (물체 기준, m)
// 데크 바닥·분수대·주차칸은 걸어 다닐 수 있게 비워 뒀어요 (건물은 campsite.js에서 바닥 모양대로 막아요)
const COLLIDERS = {
  deck: [[1.2, -1.2, 1.3, 1.3], [-1.5, 1.8, 0.9, 0.4]], // 텐트, 테이블
  caravan: [[0, 0, 1.3, 3.5], [3.25, 0, 0.6, 0.6]], // 몸체, 데크 위 테이블 (데크 바닥은 걸어 다닐 수 있어요)
}

// 물체의 위치·회전에 맞춰 충돌 상자를 실제 좌표로 바꿔 목록에 넣어요
export function addColliders(list, obj, kind) {
  const a = obj.rotation.y
  const cos = Math.cos(a)
  const sin = Math.sin(a)
  for (const [ox, oz, hw, hd] of COLLIDERS[kind] || []) {
    list.push({
      x: obj.position.x + ox * cos + oz * sin,
      z: obj.position.z - ox * sin + oz * cos,
      hw, hd, angle: a,
    })
  }
}
