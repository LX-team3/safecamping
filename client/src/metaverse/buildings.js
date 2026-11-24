// 건물 (buildings 레이어): QGIS에서 딴 건물 바닥 모양을 위로 올려서 세워요
import * as THREE from 'three'
import { centroid } from './geo.js'
import { mat, box, extrude, extrudeTex, scaleRing, patternTexture, signTexture, stripeTexture } from './shapes.js'

// type별 높이와 색. 새 type을 QGIS에 적었으면 여기에 추가해요
export const BUILDING_STYLE = {
  office: { height: 8, wall: 0xe8e6e1, roof: 0x55595e, band: 0x3d4a57 },          // 관리동: 흰 벽 + 어두운 유리 띠
  shower: { height: 4, wall: 0xd8cdb8, roof: 0x8f8f8f, band: 0x6f6a60 },          // 샤워장
  shower_toilet: { height: 4.2, wall: 0xd8cdb8, roof: 0x8f8f8f, band: 0x6f6a60 }, // 샤워장 화장실
  recicle_toilet: { height: 3.2, wall: 0x9a7650, roof: 0x5a4a3a },                // 나무 화장실
  recicle: { height: 2.6, wall: 0x3e4247, roof: 0x3e4247 },                       // 분리수거장 (루버 벽 오두막)
  toilet: { height: 2.6, wall: 0x6b4a33, roof: 0x5a3e2b },                        // 작은 나무 화장실
  car: { height: 1.6, wall: 0xf2f2f2, roof: 0xf2f2f2 },                           // 자동차 (그린 네모 크기·방향대로)
  water: { height: 2.6, open: true, roof: 0x6b4426, post: 0x6b4426 },             // 개수대: 기둥 + 지붕만 (아래로 걸어 다닐 수 있어요)
}

// 정면 찾기: 캠핑장 가운데를 가장 잘 바라보는 긴 변
// 돌려주는 값: 변의 가운데(mx, mz), 길이(len), 바깥쪽 방향(nx, nz), 변 방향(ux, uz)
function frontEdge(ring, toward) {
  const n = ring.length
  const cx = ring.reduce((s, p) => s + p[0], 0) / n
  const cz = ring.reduce((s, p) => s + p[1], 0) / n
  let best = null
  for (let i = 0; i < n; i++) {
    const [ax, az] = ring[i]
    const [bx, bz] = ring[(i + 1) % n]
    const len = Math.hypot(bx - ax, bz - az)
    if (len < 2) continue
    const ux = (bx - ax) / len
    const uz = (bz - az) / len
    const mx = (ax + bx) / 2
    const mz = (az + bz) / 2
    let nx = uz
    let nz = -ux
    if (nx * (mx - cx) + nz * (mz - cz) < 0) { nx = -nx; nz = -nz } // 건물 바깥쪽으로
    const tl = Math.hypot(toward[0] - mx, toward[1] - mz) || 1
    const facing = (nx * (toward[0] - mx) + nz * (toward[1] - mz)) / tl
    const score = len * Math.max(0.05, facing)
    if (!best || score > best.score) best = { score, mx, mz, len, nx, nz, ux, uz, i }
  }
  return best
}

// 정면 벽에 붙이는 판 (간판, 유리문)
function facadePanel(edge, width, height, y, material, out = 0.06) {
  const m = new THREE.Mesh(new THREE.PlaneGeometry(width, height), material)
  m.position.set(edge.mx + edge.nx * out, y + height / 2, edge.mz + edge.nz * out)
  m.rotation.y = Math.atan2(edge.nx, edge.nz) // 판의 앞면이 바깥을 보게
  return m
}

// 관리동: 1층 짙은 벽돌 + 큰 유리문, 흰 간판 / 콘크리트 판 / 2층 유리 벽 + 유리 난간 / 옥상 처마 + 옥상 난간
function makeOffice(ring, toward) {
  const g = new THREE.Group()
  const F1 = 3.6
  const F2 = 3.2
  const concrete = 0xbdb9b2
  const glass = new THREE.MeshStandardMaterial({ color: 0x9cc8e0, transparent: true, opacity: 0.75, roughness: 0.1, metalness: 0.3 })
  const rail = new THREE.MeshStandardMaterial({ color: 0xd6eef8, transparent: true, opacity: 0.25, side: THREE.DoubleSide })

  g.add(extrudeTex(ring, F1, stripeTexture('#3d4247', '#2c3035', 0.35)))           // 1층 짙은 벽돌
  const slab1 = extrude(scaleRing(ring, 1.04), 0.6, concrete)                         // 1층 위 콘크리트 판
  slab1.position.y = F1
  g.add(slab1)
  const floor2 = new THREE.Mesh(extrude(scaleRing(ring, 0.95), F2, 0x9cc8e0).geometry, glass) // 2층 유리 벽
  floor2.position.y = F1 + 0.6
  g.add(floor2)
  const rail1 = new THREE.Mesh(extrude(scaleRing(ring, 1.035), 1.0, 0xffffff).geometry, rail) // 2층 유리 난간
  rail1.position.y = F1 + 0.6
  g.add(rail1)
  const roof = extrude(scaleRing(ring, 1.08), 0.6, concrete)                          // 옥상 처마 (튀어나옴)
  roof.position.y = F1 + 0.6 + F2
  g.add(roof)
  const rail2 = new THREE.Mesh(extrude(scaleRing(ring, 1.07), 1.0, 0xffffff).geometry, rail) // 옥상 유리 난간
  rail2.position.y = F1 + 1.2 + F2
  g.add(rail2)

  // 정면: 큰 유리문(검은 틀) + 흰 간판
  const e = frontEdge(ring, toward)
  if (e) {
    const doorTex = patternTexture((c, n) => {
      c.fillStyle = '#1b1f24'
      c.fillRect(0, 0, n, n)
      c.strokeStyle = '#4a5058'
      c.lineWidth = 8
      c.strokeRect(0, 0, n, n)
    }, 2.2)
    const doorW = e.len * 0.6
    doorTex.repeat.set(doorW / 2.2, 1) // 2.2m마다 문틀 한 칸
    g.add(facadePanel(e, doorW, 2.8, 0, new THREE.MeshStandardMaterial({ map: doorTex, roughness: 0.15, metalness: 0.4 })))
    const sign = signTexture([
      { text: '물빛테마공원', x: 0.27, size: 70, color: '#2f3a44' },
      { text: '천도카페 편의점', x: 0.75, size: 66, color: '#3c9a4a' },
    ])
    g.add(facadePanel(e, Math.min(e.len * 0.75, 22), 1.0, F1 - 1.0 + 0.05, new THREE.MeshStandardMaterial({ map: sign }), 0.08))
  }
  return g
}

// 샤워장: 회색 가로줄 벽 + 작은 창 / 흰 지붕 / 정면 쪽 흰 차양과 네모 기둥 / 검은 간판 / 지붕 위 흰 상자와 은색 고리
function makeShower(ring, toward) {
  const g = new THREE.Group()
  const H = 3.6
  const white = 0xe9e7e2
  g.add(extrudeTex(ring, H, stripeTexture('#9a9da2', '#7f8288', 0.25)))   // 회색 사이딩 벽
  const roof = extrude(scaleRing(ring, 1.03), 0.5, white)                  // 흰 지붕
  roof.position.y = H
  g.add(roof)

  const e = frontEdge(ring, toward)
  if (e) {
    const depth = 2.6 // 차양이 앞으로 나온 길이(m)
    const yaw = Math.atan2(e.ux, e.uz)
    // 차양 판
    const canopy = box(0.5, 0.5, e.len, white, e.mx + e.nx * depth / 2, H, e.mz + e.nz * depth / 2)
    canopy.scale.x = depth / 0.5
    canopy.rotation.y = yaw
    g.add(canopy)
    // 네모 기둥: 차양 앞쪽 끝을 따라 약 3.5m 간격
    const count = Math.max(2, Math.round(e.len / 3.5) + 1)
    for (let i = 0; i < count; i++) {
      const t = -0.5 + i / (count - 1)
      const x = e.mx + e.ux * e.len * t * 0.96 + e.nx * (depth - 0.3)
      const z = e.mz + e.uz * e.len * t * 0.96 + e.nz * (depth - 0.3)
      const col = box(0.45, H, 0.45, white, x, 0, z)
      col.rotation.y = yaw
      g.add(col)
    }
    // 정면 벽의 작은 네모 창 (약 3m 간격)
    const winMat = new THREE.MeshStandardMaterial({ color: 0x2a2d31 })
    const wins = Math.max(1, Math.floor(e.len / 3))
    for (let i = 0; i < wins; i++) {
      const t = (i + 0.5) / wins - 0.5
      const w = facadePanel({ ...e, mx: e.mx + e.ux * e.len * t, mz: e.mz + e.uz * e.len * t }, 0.6, 0.6, 2.4, winMat)
      g.add(w)
    }
    // 검은 간판 (차양 앞면)
    const sign = signTexture([
      { text: 'SHOWER', x: 0.13, size: 54, color: '#ffffff' },
      { text: '샤워장 화장실', x: 0.5, size: 76, color: '#ffffff' },
      { text: 'TOILET', x: 0.87, size: 54, color: '#ffffff' },
    ], { bg: '#1c1c1c' })
    const front = { ...e, mx: e.mx + e.nx * depth, mz: e.mz + e.nz * depth }
    g.add(facadePanel(front, Math.min(e.len * 0.5, 11), 0.7, H - 0.1, new THREE.MeshStandardMaterial({ map: sign }), 0.27))
    // 지붕 위 흰 상자 + 은색 고리 2개
    const n = ring.length
    const cx = ring.reduce((s, p) => s + p[0], 0) / n
    const cz = ring.reduce((s, p) => s + p[1], 0) / n
    const top = box(5, 1.3, 3, white, cx - e.ux * e.len * 0.2, H + 0.5, cz - e.uz * e.len * 0.2)
    top.rotation.y = yaw
    g.add(top)
    const metal = new THREE.MeshStandardMaterial({ color: 0xcfd3d6, metalness: 0.8, roughness: 0.3 })
    for (const k of [0.05, 0.15]) {
      const ring2 = new THREE.Mesh(new THREE.TorusGeometry(0.4, 0.05, 6, 12, Math.PI), metal)
      ring2.position.set(e.mx + e.ux * e.len * k - e.nx * 0.5, H + 0.5, e.mz + e.uz * e.len * k - e.nz * 0.5)
      ring2.rotation.y = yaw + Math.PI / 2
      g.add(ring2)
    }
  }
  return g
}

// 변 하나를 따라 세우는 얇은 벽판 (분리수거장 루버 벽 등)
function edgePanel(a, b, height, y, material, thick = 0.08) {
  const len = Math.hypot(b[0] - a[0], b[1] - a[1])
  const m = new THREE.Mesh(new THREE.BoxGeometry(thick, height, len), material)
  m.position.set((a[0] + b[0]) / 2, y + height / 2, (a[1] + b[1]) / 2)
  m.rotation.y = Math.atan2(b[0] - a[0], b[1] - a[1])
  return m
}

// 작은 나무 화장실: 갈색 가로 판자 벽 / 앞으로 나온 갈색 지붕 / 정면에 문 2개 + 가운데 흰 판(화장실 표시) / 앞에 빨간 매트
function makeToilet(ring, toward) {
  const g = new THREE.Group()
  const H = 2.6
  g.add(extrudeTex(ring, H, stripeTexture('#6b4a33', '#553926', 0.22))) // 갈색 판자 벽
  const roof = extrude(scaleRing(ring, 1.18), 0.25, 0x5a3e2b)                         // 넉넉히 튀어나온 지붕
  roof.position.y = H
  g.add(roof)

  const e = frontEdge(ring, toward)
  if (e) {
    const at = (t) => ({ ...e, mx: e.mx + e.ux * e.len * t, mz: e.mz + e.uz * e.len * t })
    const doorMat = new THREE.MeshStandardMaterial({ color: 0x4b3a30 })
    g.add(facadePanel(at(-0.28), Math.min(1.0, e.len * 0.25), 2.0, 0.1, doorMat))   // 왼쪽 문
    g.add(facadePanel(at(0.28), Math.min(1.0, e.len * 0.25), 2.0, 0.1, doorMat))    // 오른쪽 문
    const sign = signTexture([
      { text: '🚻', x: 0.5, size: 150 },
      { text: 'TOILET', x: 0.5, size: 40, color: '#1d4f91' },
    ], { bg: '#f4f4f2', w: 256, h: 256 })
    g.add(facadePanel(at(0), Math.min(0.9, e.len * 0.2), 2.0, 0.1, new THREE.MeshStandardMaterial({ map: sign }), 0.07)) // 가운데 흰 판
    // 앞 빨간 매트
    const mat2 = new THREE.Mesh(new THREE.PlaneGeometry(e.len * 0.85, 1.4), new THREE.MeshStandardMaterial({ color: 0xa8443a }))
    mat2.rotation.x = -Math.PI / 2
    mat2.rotation.z = Math.atan2(-e.uz, e.ux) // 매트 긴 쪽이 정면 벽을 따라가게
    mat2.position.set(e.mx + e.nx * 0.8, 0.12, e.mz + e.nz * 0.8)
    g.add(mat2)
  }
  return g
}

// 분리수거장: 짙은 회색 기둥 / 아래쪽 루버(가로 날개) 벽 / 위쪽 반투명 판 / 반투명 지붕 / 정면은 열려 있고 간판
// 안에는 색깔별 분리수거함
function makeRecycle(ring, toward) {
  const g = new THREE.Group()
  const H = 2.6
  const frame = new THREE.MeshStandardMaterial({ color: 0x3e4247, metalness: 0.4, roughness: 0.5 })
  const clear = new THREE.MeshStandardMaterial({ color: 0xc9d3d8, transparent: true, opacity: 0.35, side: THREE.DoubleSide })
  const louverTex = (len) => {
    const t = patternTexture((c, n) => {
      c.fillStyle = '#4a4f55'
      c.fillRect(0, 0, n, n)
      c.fillStyle = '#2e3236'
      c.fillRect(0, n * 0.75, n, n * 0.25)
    }, 1)
    t.repeat.set(len / 1.5, 1.4 / 0.14) // 날개 간격 14cm
    return t
  }

  const e = frontEdge(ring, toward)
  const n = ring.length
  for (let i = 0; i < n; i++) {
    const a = ring[i]
    const b = ring[(i + 1) % n]
    // 기둥
    const post = new THREE.Mesh(new THREE.BoxGeometry(0.15, H, 0.15), frame)
    post.position.set(a[0], H / 2, a[1])
    g.add(post)
    if (e && i === e.i) continue // 정면은 열어 둬요
    const len = Math.hypot(b[0] - a[0], b[1] - a[1])
    g.add(edgePanel(a, b, 1.4, 0.1, new THREE.MeshStandardMaterial({ map: louverTex(len) }))) // 아래 루버 벽
    g.add(edgePanel(a, b, 0.9, 1.6, clear, 0.04))                                            // 위 반투명 판
  }
  // 지붕판과 지붕 틀의 윗면이 같은 높이에 겹치면 화면이 반짝거려요(두 면이 서로 앞에 나오려고 다툼).
  // 그래서 틀을 낮추고 지붕판을 그 위로 6cm 띄워서, 두 면이 겹치지 않게 했어요
  const roof = new THREE.Mesh(extrude(scaleRing(ring, 1.05), 0.12, 0xffffff).geometry, clear) // 반투명 지붕 (H+0.08 ~ H+0.20)
  roof.position.y = H + 0.08
  g.add(roof)
  const roofFrame = extrude(scaleRing(ring, 1.06), 0.1, 0x3e4247)                          // 지붕 틀 (H-0.08 ~ H+0.02)
  roofFrame.position.y = H - 0.08
  g.add(roofFrame)

  if (e) {
    // 정면 윗보 + 간판
    const beam = edgePanel([e.mx - e.ux * e.len / 2, e.mz - e.uz * e.len / 2], [e.mx + e.ux * e.len / 2, e.mz + e.uz * e.len / 2], 0.45, H - 0.45, frame, 0.15)
    g.add(beam)
    const sign = signTexture([
      { text: '♻ 쓰레기 분리수거', x: 0.5, size: 64, color: '#ffffff' },
    ], { bg: '#3e4247', w: 1024, h: 128 })
    g.add(facadePanel(e, Math.min(e.len * 0.6, 3.5), 0.38, H - 0.42, new THREE.MeshStandardMaterial({ map: sign }), 0.09))
    // 분리수거함 (안쪽 뒤편에 나란히)
    const colors = [0x2f7fc1, 0xf2c14e, 0x4f9a52, 0x9aa0a6]
    const cx = ring.reduce((s2, p) => s2 + p[0], 0) / n
    const cz = ring.reduce((s2, p) => s2 + p[1], 0) / n
    colors.forEach((c, k) => {
      const t = (k - (colors.length - 1) / 2) * 0.9
      const bin = box(0.7, 1.0, 0.6, c, cx + e.ux * t - e.nx * 0.4, 0.1, cz + e.uz * t - e.nz * 0.4)
      bin.rotation.y = Math.atan2(e.ux, e.uz)
      g.add(bin)
    })
  }
  return g
}

// 자동차: 그린 네모의 긴 변 = 차 길이, 짧은 변 = 차 폭. 색은 자리마다 달라요
const CAR_COLORS = [0xf2f2f2, 0x2b2f36, 0x9aa3ab, 0x7a1f24, 0x2d4f7c]
export function makeCar(ring) { // 달리는 차(cars.js)도 같이 써요
  // 가장 긴 변의 방향과 길이, 그에 수직인 폭 구하기
  let best = { len: 0, ux: 1, uz: 0 }
  for (let i = 0; i < ring.length; i++) {
    const [ax, az] = ring[i]
    const [bx, bz] = ring[(i + 1) % ring.length]
    const len = Math.hypot(bx - ax, bz - az)
    if (len > best.len) best = { len, ux: (bx - ax) / len, uz: (bz - az) / len }
  }
  let wMin = Infinity, wMax = -Infinity, lMin = Infinity, lMax = -Infinity
  for (const [x, z] of ring) {
    const along = x * best.ux + z * best.uz
    const across = -x * best.uz + z * best.ux
    lMin = Math.min(lMin, along); lMax = Math.max(lMax, along)
    wMin = Math.min(wMin, across); wMax = Math.max(wMax, across)
  }
  const L = Math.max(3, lMax - lMin) * 0.95
  const W = Math.max(1.5, wMax - wMin) * 0.9
  const seed = Math.abs(Math.round(ring[0][0] * 7 + ring[0][1] * 13))
  const color = CAR_COLORS[seed % CAR_COLORS.length]

  const car = new THREE.Group()
  car.add(box(W, 0.75, L, color, 0, 0.3))                         // 차체 (길이 방향 = 로컬 z)
  car.add(box(W * 0.85, 0.6, L * 0.5, color, 0, 1.05, -L * 0.05)) // 지붕
  const glass = new THREE.MeshStandardMaterial({ color: 0x2a3440, roughness: 0.2, metalness: 0.4 })
  const win = new THREE.Mesh(new THREE.BoxGeometry(W * 0.87, 0.45, L * 0.46), glass) // 창문 띠
  win.position.set(0, 1.3, -L * 0.05)
  car.add(win)
  const tire = mat(0x1c1c1c)
  for (const sx of [-1, 1]) {
    for (const sz of [-1, 1]) {
      const w = new THREE.Mesh(new THREE.CylinderGeometry(0.33, 0.33, 0.25, 10), tire)
      w.rotation.z = Math.PI / 2
      w.position.set(sx * (W / 2 - 0.05), 0.33, sz * L * 0.32)
      car.add(w)
    }
  }
  // 로컬 z(차 길이)를 가장 긴 변 방향으로 돌리기
  car.rotation.y = Math.atan2(best.ux, best.uz)
  const g = new THREE.Group()
  g.add(car)
  return g
}

export function makeBuilding(ringWorld, type, heightAt, toward = [0, 0]) {
  const style = BUILDING_STYLE[type] || { height: 4, wall: 0xcccccc, roof: 0x888888 }
  if (!BUILDING_STYLE[type]) console.warn('모양이 정해지지 않은 buildings type:', type)
  const [cx, cz] = centroid(ringWorld)
  const ring = ringWorld.map(([x, z]) => [x - cx, z - cz]) // 건물 가운데 기준
  const h = style.height
  let g = new THREE.Group()
  const towardLocal = [toward[0] - cx, toward[1] - cz] // 캠핑장 가운데 (건물 기준 좌표)

  if (type === 'office') {
    g = makeOffice(ring, towardLocal)
  } else if (type === 'shower' || type === 'shower_toilet') {
    g = makeShower(ring, towardLocal)
  } else if (type === 'toilet' || type === 'recicle_toilet') {
    g = makeToilet(ring, towardLocal)
  } else if (type === 'recicle') {
    g = makeRecycle(ring, towardLocal)
  } else if (type === 'car') {
    g = makeCar(ring)
  } else if (style.open) {
    // 지붕만 있는 쉼터: 꼭짓점마다 기둥 + 지붕 판 + 가운데 싱크대
    for (const [x, z] of ring.slice(0, 8)) g.add(box(0.3, h, 0.3, style.post, x * 0.92, 0, z * 0.92))
    const roof = extrude(scaleRing(ring, 1.05), 0.25, style.roof)
    roof.position.y = h
    g.add(roof)
    g.add(box(3, 0.9, 1, 0xb8c2c8))
  } else {
    g.add(extrude(ring, h, style.wall)) // 벽
    if (style.band) {
      const band = extrude(scaleRing(ring, 1.01), h * 0.18, style.band) // 창문 띠
      band.position.y = h * 0.5
      g.add(band)
    }
    const roof = extrude(scaleRing(ring, 1.04), 0.4, style.roof) // 지붕 (살짝 튀어나오게)
    roof.position.y = h
    g.add(roof)
  }

  g.position.set(cx, heightAt(cx, cz), cz)
  g.userData = { type, building: true }
  return g
}
