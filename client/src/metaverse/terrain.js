import * as THREE from 'three'
import { pointInRing, distToRing } from './geo.js'

// asc 파일을 읽어서 3D 지형을 만들어요
// flatArea: 평평하게 깎을 영역(EPSG:5179 좌표의 다각형). 캠핑장 테두리를 넣어요
// carveAreas: 낮게 파낼 영역 목록(EPSG:5179 다각형들). 강바닥(river의 bed)을 넣어요
// carveDepth: 캠핑장 바닥보다 몇 m 낮게 팔지
export async function loadTerrain(url, { exaggeration = 1.15, flatArea = null, carveAreas = [], carveDepth = 3 } = {}) {
  const text = await (await fetch(url)).text()
  const lines = text.trim().split(/\r?\n/)

  // 1) 머리말 6줄
  const head = {}
  for (let i = 0; i < 6; i++) {
    const [key, value] = lines[i].trim().split(/\s+/)
    head[key.toLowerCase()] = Number(value)
  }
  const cols = head.ncols
  const rows = head.nrows
  const cell = head.cellsize
  const nodata = head.nodata_value ?? -9999

  // 2) 지형 한가운데의 실제 좌표 (GeoJSON 좌표를 3D 좌표로 바꿀 때 기준점)
  const cx = head.xllcorner + (cols * cell) / 2
  const cy = head.yllcorner + (rows * cell) / 2

  // 3) 높이값 읽기
  const raw = lines.slice(6).join(' ').trim().split(/\s+/).map(Number)
  const center = raw[Math.floor(rows / 2) * cols + Math.floor(cols / 2)]
  const width = (cols - 1) * cell
  const depth = (rows - 1) * cell

  // 점 번호 → 3D 좌표 (첫 줄이 북쪽 = z가 작은 쪽)
  const vx = (i) => -width / 2 + (i % cols) * cell
  const vz = (i) => -depth / 2 + Math.floor(i / cols) * cell

  // 가운데를 0으로 맞춘 높이
  const hs = raw.map((v) => (v === nodata || Number.isNaN(v) ? 0 : (v - center) * exaggeration))

  // 4) 캠핑장 안쪽을 평평하게 깎기
  let floor = 0
  const campRing = flatArea ? flatArea.map(([X, Y]) => [X - cx, -(Y - cy)]) : null
  if (campRing) {
    const ring = campRing
    const insideHs = []
    for (let i = 0; i < hs.length; i++) if (pointInRing(vx(i), vz(i), ring)) insideHs.push(hs[i])
    insideHs.sort((a, b) => a - b)
    if (insideHs.length) floor = insideHs[Math.floor(insideHs.length / 2)] // 가운데값

    for (let i = 0; i < hs.length; i++) {
      const x = vx(i)
      const z = vz(i)
      const d = pointInRing(x, z, ring) ? 0 : distToRing(x, z, ring)
      if (d < 30) hs[i] = floor                                        // 안쪽 + 테두리 30m: 완전히 평평
      else if (d < 70) hs[i] = floor + (hs[i] - floor) * ((d - 30) / 40) // 30~70m: 자연스럽게 이어지기
    }
  }

  // 4-2) 강바닥 파내기: 캠핑장보다 carveDepth만큼 낮게. 둘레 25m는 비탈로 이어져요
  const bedLevel = floor - carveDepth
  if (carveAreas.length) {
    const rings = carveAreas.map((r) => r.map(([X, Y]) => [X - cx, -(Y - cy)]))
    for (let i = 0; i < hs.length; i++) {
      const x = vx(i)
      const z = vz(i)
      // 캠핑장 안은 그대로 둬요. 바로 바깥부터는 강바닥까지 파낼 수 있어요
      // (캠핑장 안 물건이 꺼지지 않게 하는 건 아래 heightAt이 맡아요)
      if (campRing && pointInRing(x, z, campRing)) continue
      let d = Infinity
      for (const r of rings) {
        if (pointInRing(x, z, r)) { d = 0; break }
        d = Math.min(d, distToRing(x, z, r))
      }
      const low = bedLevel - 0.5 // 강바닥 그림보다 살짝 아래로 (땅이 뚫고 올라오지 않게)
      if (d === 0) hs[i] = low
      else if (d < 25) hs[i] = Math.min(hs[i], low + (hs[i] - low) * (d / 25))
    }
  }

  // 5) 판을 만들고 높이와 색 넣기
  const geo = new THREE.PlaneGeometry(width, depth, cols - 1, rows - 1)
  geo.rotateX(-Math.PI / 2)
  const pos = geo.attributes.position
  const colors = []
  const low = new THREE.Color(0xa7c97a)
  const mid = new THREE.Color(0x4f7f3f)
  const high = new THREE.Color(0x8f8a7c)
  for (let i = 0; i < pos.count; i++) {
    pos.setY(i, hs[i])
    const h = hs[i] - floor
    const c = h < 150
      ? low.clone().lerp(mid, Math.max(0, h) / 150)
      : mid.clone().lerp(high, Math.min(1, (h - 150) / 400))
    colors.push(c.r, c.g, c.b)
  }
  geo.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3))
  geo.computeVertexNormals()

  const mesh = new THREE.Mesh(
    geo,
    new THREE.MeshStandardMaterial({ vertexColors: true, flatShading: true })
  )

  // 6) 아무 위치(x, z)의 땅 높이를 알려주는 함수
  function heightAt(x, z) {
    // 캠핑장 안은 항상 평평한 바닥 높이예요.
    // 지형 점이 20m 간격이라, 바깥이 강으로 파이면 가장자리 높이가 같이 끌려 내려가는데
    // 그걸 막아서 도로·데크·캐릭터가 꺼지지 않아요
    if (campRing && pointInRing(x, z, campRing)) return floor
    const fc = (x + width / 2) / cell
    const fr = (z + depth / 2) / cell
    const c0 = Math.max(0, Math.min(cols - 2, Math.floor(fc)))
    const r0 = Math.max(0, Math.min(rows - 2, Math.floor(fr)))
    const tx = Math.max(0, Math.min(1, fc - c0))
    const tz = Math.max(0, Math.min(1, fr - r0))
    const a = hs[r0 * cols + c0]
    const b = hs[r0 * cols + c0 + 1]
    const c = hs[(r0 + 1) * cols + c0]
    const d = hs[(r0 + 1) * cols + c0 + 1]
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz
  }

  return { mesh, heightAt, floor, bedLevel, cx, cy, width, depth }
}
