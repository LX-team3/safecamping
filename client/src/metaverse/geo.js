// GeoJSON 파일 읽기 + 도형 계산 모음
// 여러 파일(지형, 캠핑장, 캐릭터, 퀴즈)이 같은 계산을 쓰기 때문에 한곳에 모았어요
// 여기서 [x, z]는 3D 화면 좌표, ring은 다각형 꼭짓점 목록 [[x, z], ...] 이에요

// ---------- 파일 읽기 ----------
// 선택 파일: 파일이 없거나 내용이 잘못돼도 오류 없이 null을 돌려줘요
export async function loadGeoOptional(name) {
  try {
    const res = await fetch(`${import.meta.env.BASE_URL}metaverse/data/${name}.geojson`)
    if (!res.ok) return null
    const data = await res.json()
    return Array.isArray(data?.features) ? data : null
  } catch {
    return null
  }
}

// 필수 파일: 없으면 게임을 만들 수 없어서, 이유를 알 수 있게 오류를 내요
export async function loadGeo(name) {
  const data = await loadGeoOptional(name)
  if (!data) throw new Error(`필수 파일 public/metaverse/data/${name}.geojson 을 읽지 못했어요`)
  return data
}

// Point, LineString, Polygon과 Multi 버전을 모두 "점 배열 목록"으로 바꿔요
export function parts(geom) {
  if (!geom) return []
  switch (geom.type) {
    case 'Point': return [[geom.coordinates]]
    case 'MultiPoint': return geom.coordinates.map((p) => [p])
    case 'LineString': return [geom.coordinates]
    case 'MultiLineString': return geom.coordinates
    case 'Polygon': return [geom.coordinates[0]]
    case 'MultiPolygon': return geom.coordinates.map((p) => p[0])
    default: return []
  }
}

// 폴리곤을 [바깥 테두리, 구멍1, 구멍2, ...] 목록으로. 고리 모양 도로의 가운데 잔디가 '구멍'이에요
export function polygonsWithHoles(geom) {
  if (!geom) return []
  if (geom.type === 'Polygon') return [geom.coordinates]
  if (geom.type === 'MultiPolygon') return geom.coordinates
  return []
}

// boundary.geojson의 첫 번째 테두리 (EPSG:5179 좌표 그대로)
export function firstRing(geojson) {
  return parts(geojson.features[0].geometry)[0].map((p) => [p[0], p[1]])
}

// GeoJSON 다각형은 마지막 점이 첫 점과 같아요. 가운데 계산이 틀어지지 않게 빼요
export function openRing(ring) {
  const a = ring[0]
  const b = ring[ring.length - 1]
  return ring.length > 3 && a[0] === b[0] && a[1] === b[1] ? ring.slice(0, -1) : ring
}

// ---------- 도형 계산 ----------
// 점(px, pz)이 다각형 ring 안에 있는지
export function pointInRing(px, pz, ring) {
  let ok = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, zi] = ring[i]
    const [xj, zj] = ring[j]
    if ((zi > pz) !== (zj > pz) && px < ((xj - xi) * (pz - zi)) / (zj - zi) + xi) ok = !ok
  }
  return ok
}

// 점이 도로 모양 {outer, holes} 안에 있는지 (구멍 안이면 도로가 아니에요)
export function inShape(px, pz, shape) {
  return pointInRing(px, pz, shape.outer) && !shape.holes.some((h) => pointInRing(px, pz, h))
}

// 점에서 선(pts)까지 가장 가까운 곳. closed가 true면 마지막 점과 첫 점도 이어서 봐요 (테두리용)
// 돌려주는 값: 가장 가까운 점(x, z)과 거리(d)
export function nearestOnSegs(px, pz, pts, closed = false) {
  let best = { x: px, z: pz, d: Infinity }
  const n = closed ? pts.length : pts.length - 1
  for (let i = 0; i < n; i++) {
    const [ax, az] = pts[i]
    const [bx, bz] = pts[(i + 1) % pts.length]
    const dx = bx - ax
    const dz = bz - az
    const t = Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz || 1)))
    const x = ax + t * dx
    const z = az + t * dz
    const d = Math.hypot(px - x, pz - z)
    if (d < best.d) best = { x, z, d }
  }
  return best
}

// 점에서 선까지 거리(m)
export function distToSegs(px, pz, pts, closed = false) {
  return nearestOnSegs(px, pz, pts, closed).d
}

// 점에서 다각형 테두리까지 거리(m)
export function distToRing(px, pz, ring) {
  return nearestOnSegs(px, pz, ring, true).d
}

// 다각형의 가운데 (꼭짓점 평균)
export function centroid(ring) {
  const n = ring.length
  return ring.reduce((s, [x, z]) => [s[0] + x / n, s[1] + z / n], [0, 0])
}

// 선 위의 t(0~1) 지점 위치와 방향
export function pointAlong(pts, t) {
  const seg = []
  let total = 0
  for (let i = 1; i < pts.length; i++) {
    const l = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1])
    seg.push(l)
    total += l
  }
  let goal = t * total
  for (let i = 1; i < pts.length; i++) {
    if (goal <= seg[i - 1] || i === pts.length - 1) {
      const k = seg[i - 1] ? Math.min(1, goal / seg[i - 1]) : 0
      const [ax, az] = pts[i - 1]
      const [bx, bz] = pts[i]
      return { x: ax + (bx - ax) * k, z: az + (bz - az) * k, angle: Math.atan2(bx - ax, bz - az) }
    }
    goal -= seg[i - 1]
  }
  return { x: pts[0][0], z: pts[0][1], angle: 0 }
}

// ---------- 부딪힘(충돌) 검사 ----------
// 점(px, pz)이 충돌 영역 안에 있거나 margin(m)보다 가까우면 true
// 충돌 영역은 두 가지예요:
//   { poly }                    건물 바닥 모양(다각형)
//   { x, z, hw, hd, angle }     돌아간 네모 상자 (가운데, 가로·세로 절반, 각도)
export function hitsCollider(px, pz, colliders, margin = 0) {
  for (const c of colliders) {
    if (c.poly) {
      if (pointInRing(px, pz, c.poly) || distToRing(px, pz, c.poly) < margin) return true
      continue
    }
    const dx = px - c.x
    const dz = pz - c.z
    const cos = Math.cos(c.angle)
    const sin = Math.sin(c.angle)
    const lx = dx * cos - dz * sin // 상자 기준 좌표로 돌려서 비교
    const lz = dx * sin + dz * cos
    if (Math.abs(lx) < c.hw + margin && Math.abs(lz) < c.hd + margin) return true
  }
  return false
}
