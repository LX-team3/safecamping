// 모양 만드는 도우미: 재질, 상자, 무늬 그림, 바닥 면, 입체, 띠
// 캠핑장의 여러 파일(바닥, 강, 건물, 데크, 나무)이 같이 써요
import * as THREE from 'three'

// ---------- 재질 ----------
// 같은 색 재질은 한 번만 만들어서 같이 써요.
// (상자마다 새 재질을 만들면 그래픽 메모리를 낭비해요)
const matCache = new Map()
export function mat(color, extra) {
  // 투명도 같은 특별한 설정이 있으면 따로 만들어요
  if (extra) return new THREE.MeshStandardMaterial({ color, flatShading: true, ...extra })
  if (!matCache.has(color)) matCache.set(color, new THREE.MeshStandardMaterial({ color, flatShading: true }))
  return matCache.get(color)
}

// 바닥에 까는 면은 지형보다 살짝 앞에 그려지게 해요 (깜빡임 방지)
const flatCache = new Map()
export function flatMat(color) {
  if (!flatCache.has(color)) {
    flatCache.set(color, mat(color, { side: THREE.DoubleSide, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 }))
  }
  return flatCache.get(color)
}

// ---------- 상자 ----------
export function box(w, h, d, color, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat(color))
  m.position.set(x, y + h / 2, z) // y는 바닥 높이
  return m
}

// ---------- 무늬 그림 ----------
// 바닥·벽 무늬를 그림으로 만들어요. size: 무늬 한 칸의 실제 크기(m)
export function patternTexture(draw, size) {
  const c = document.createElement('canvas')
  c.width = c.height = 128
  draw(c.getContext('2d'), 128)
  const tex = new THREE.CanvasTexture(c)
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping
  tex.repeat.set(1 / size, 1 / size) // 좌표가 m 단위라서 size m마다 무늬가 한 번 반복돼요
  tex.colorSpace = THREE.SRGBColorSpace
  return tex
}

// 가로줄 무늬 벽 (벽돌·사이딩). gap: 줄 간격(m). 같은 무늬는 한 번만 만들어요
const stripeCache = new Map()
export function stripeTexture(base, line, gap) {
  const key = `${base}|${line}|${gap}`
  if (!stripeCache.has(key)) {
    stripeCache.set(key, patternTexture((g, n) => {
      g.fillStyle = base
      g.fillRect(0, 0, n, n)
      g.fillStyle = line
      g.fillRect(0, n * 0.82, n, n * 0.18)
    }, gap))
  }
  return stripeCache.get(key)
}

// 글자 간판 그림
export function signTexture(lines, { bg = '#ffffff', fg = '#333333', w = 1024, h = 128 } = {}) {
  const c = document.createElement('canvas')
  c.width = w
  c.height = h
  const g = c.getContext('2d')
  g.fillStyle = bg
  g.fillRect(0, 0, w, h)
  g.textBaseline = 'middle'
  for (const t of lines) {
    g.fillStyle = t.color || fg
    g.font = `bold ${t.size || 64}px sans-serif`
    g.textAlign = t.align || 'center'
    g.fillText(t.text, t.x * w, h / 2)
  }
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  return tex
}

// ---------- 다각형으로 만드는 면과 입체 ----------
// ring(꼭짓점 목록)을 three.js 도형으로. holes: 가운데 뚫린 구멍들
function shapeOf(ring, holes = []) {
  const shape = new THREE.Shape(ring.map(([x, z]) => new THREE.Vector2(x, -z)))
  for (const h of holes) shape.holes.push(new THREE.Path(h.map(([x, z]) => new THREE.Vector2(x, -z))))
  return shape
}

// 바닥에 눕힌 평평한 면 (잔디, 도로, 강물)
export function flatPolygon(ring, material, y, holes = []) {
  const geo = new THREE.ShapeGeometry(shapeOf(ring, holes))
  geo.rotateX(-Math.PI / 2)
  const mesh = new THREE.Mesh(geo, material)
  mesh.position.y = y
  return mesh
}

// 바닥 모양(ring, 물체 가운데 기준 좌표)을 위로 depth만큼 올린 입체
export function extrude(ring, depth, color) {
  const geo = new THREE.ExtrudeGeometry(shapeOf(ring), { depth, bevelEnabled: false })
  geo.rotateX(-Math.PI / 2) // 위로 올라가게 눕히기
  return new THREE.Mesh(geo, mat(color))
}

// 무늬를 입힌 입체 (벽용)
export function extrudeTex(ring, depth, texture, extra = {}) {
  const geo = new THREE.ExtrudeGeometry(shapeOf(ring), { depth, bevelEnabled: false })
  geo.rotateX(-Math.PI / 2)
  return new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ map: texture, ...extra }))
}

// 가운데를 기준으로 k배 키운 모양 (지붕 처마, 유리 띠용)
export function scaleRing(ring, k) {
  return ring.map(([x, z]) => [x * k, z * k])
}

// ---------- 선으로 만드는 띠 ----------
// 선을 일정 간격(step m)으로 촘촘하게
export function densify(pts, step) {
  const out = [pts[0]]
  for (let i = 1; i < pts.length; i++) {
    const [ax, az] = pts[i - 1]
    const [bx, bz] = pts[i]
    const n = Math.max(1, Math.ceil(Math.hypot(bx - ax, bz - az) / step))
    for (let k = 1; k <= n; k++) out.push([ax + ((bx - ax) * k) / n, az + ((bz - az) * k) / n])
  }
  return out
}

// 선을 따라 폭이 있는 띠(개울)를 만들어요
export function ribbon(pts, width, yOf, material) {
  const pos = []
  const idx = []
  for (let i = 0; i < pts.length; i++) {
    const a = pts[Math.max(0, i - 1)]
    const b = pts[Math.min(pts.length - 1, i + 1)]
    let dx = b[0] - a[0]
    let dz = b[1] - a[1]
    const len = Math.hypot(dx, dz) || 1
    dx /= len
    dz /= len
    const nx = (-dz * width) / 2
    const nz = (dx * width) / 2
    const [x, z] = pts[i]
    const y = yOf(x, z)
    pos.push(x + nx, y, z + nz, x - nx, y, z - nz)
    if (i > 0) {
      const k = (i - 1) * 2
      idx.push(k, k + 1, k + 2, k + 1, k + 3, k + 2)
    }
  }
  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3))
  geo.setIndex(idx)
  geo.computeVertexNormals()
  return new THREE.Mesh(geo, material)
}

// ---------- 정리 ----------
// 물체(와 그 안의 모든 것)가 쓰던 그래픽 메모리를 돌려줘요.
// 화면에서 빼기만 하면 메모리는 그대로 남아서, 다 쓴 물체는 꼭 이걸로 정리해요
export function disposeTree(root) {
  root.traverse((o) => {
    o.geometry?.dispose()
    const mats = Array.isArray(o.material) ? o.material : o.material ? [o.material] : []
    for (const m of mats) {
      m.map?.dispose()
      m.dispose()
    }
  })
}
