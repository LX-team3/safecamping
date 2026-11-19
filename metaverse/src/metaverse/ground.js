// 바닥 영역 (areas 레이어): 보도블록, 주차장, 분수대 광장, 도로, 바닥분수
import * as THREE from 'three'
import { pointInRing, centroid } from './geo.js'
import { patternTexture, flatPolygon } from './shapes.js'

// type별 바닥 무늬. 새 type을 QGIS에 적었으면 여기에 추가해요
const AREA_STYLE = {
  // 흰 보도블록: 밝은 바탕 + 2m 격자 줄눈
  plaza: () => patternTexture((g, n) => {
    g.fillStyle = '#e6e3dc'
    g.fillRect(0, 0, n, n)
    g.strokeStyle = '#cfcac0'
    g.lineWidth = 4
    g.strokeRect(0, 0, n, n)
  }, 2),
  // 도로: 짙은 아스팔트
  road: () => patternTexture((g, n) => {
    g.fillStyle = '#5a5f64'
    g.fillRect(0, 0, n, n)
    g.fillStyle = '#62676c'
    for (let i = 0; i < 12; i++) g.fillRect((i * 41) % n, (i * 67) % n, 5, 5)
  }, 4),
  // 주차장: 아스팔트
  parking: () => patternTexture((g, n) => {
    g.fillStyle = '#6b7075'
    g.fillRect(0, 0, n, n)
  }, 5),
  // 분수대 광장: 밝은 화강석 + 어두운 띠 (4m마다)
  fountain: () => patternTexture((g, n) => {
    g.fillStyle = '#dcd8d0'
    g.fillRect(0, 0, n, n)
    g.fillStyle = '#7d7a75'
    g.fillRect(0, n * 0.42, n, n * 0.16)
  }, 4),
}

// 같은 type의 바닥은 재질(무늬 그림 포함)을 한 번만 만들어서 같이 써요
const areaMats = new Map()
function areaMaterial(type) {
  if (!areaMats.has(type)) {
    const style = AREA_STYLE[type]
    if (!style) console.warn('바닥 모양이 정해지지 않은 areas type:', type)
    areaMats.set(type, new THREE.MeshStandardMaterial({
      color: style ? 0xffffff : 0xcccccc,
      map: style ? style() : null,
      side: THREE.DoubleSide,
      polygonOffset: true,
      polygonOffsetFactor: -3,
      polygonOffsetUnits: -3,
    }))
  }
  return areaMats.get(type)
}

// 바닥 영역 하나. holes: 가운데 뚫린 구멍 (고리 모양 도로의 안쪽 잔디)
export function makeArea(ring, type, y, holes = []) {
  return flatPolygon(ring, areaMaterial(type), y, holes)
}

// ---------- 바닥분수 (areas 레이어의 type: splash) ----------
// 젖은 돌바닥 + 3m 간격으로 물줄기가 솟았다 내려갔다 해요
function wetTexture() {
  return patternTexture((g, n) => {
    g.fillStyle = '#9fb0b8'
    g.fillRect(0, 0, n, n)
    g.strokeStyle = '#8a9aa2'
    g.lineWidth = 4
    g.strokeRect(0, 0, n, n)
  }, 1.5)
}

export function makeSplash(ringWorld, y) {
  const [cx, cz] = centroid(ringWorld)
  const ring = ringWorld.map(([x, z]) => [x - cx, z - cz]) // 가운데 기준
  const g = new THREE.Group()

  // 젖은 바닥
  g.add(flatPolygon(ring, new THREE.MeshStandardMaterial({ map: wetTexture(), roughness: 0.3, side: THREE.DoubleSide }), 0.05))

  // 물줄기: 영역 안에 3m 간격으로 (모양과 재질은 하나를 같이 써요)
  const jetGeo = new THREE.CylinderGeometry(0.08, 0.15, 1, 6)
  const jetMat = new THREE.MeshStandardMaterial({ color: 0xe8f6ff, transparent: true, opacity: 0.7 })
  const jets = []
  let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity
  for (const [x, z] of ring) {
    minX = Math.min(minX, x); maxX = Math.max(maxX, x)
    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z)
  }
  for (let x = minX + 1.5; x < maxX; x += 3) {
    for (let z = minZ + 1.5; z < maxZ; z += 3) {
      if (!pointInRing(x, z, ring) || jets.length >= 60) continue
      const jet = new THREE.Mesh(jetGeo, jetMat)
      jet.position.set(x, 0, z)
      jet.userData.phase = (x * 0.7 + z * 1.3) % (Math.PI * 2) // 물줄기마다 박자를 다르게
      g.add(jet)
      jets.push(jet)
    }
  }

  g.position.set(cx, y, cz)
  g.userData = { type: 'fountain' } // 분수대 퀴즈가 이 위치를 찾아요

  let time = 0
  g.userData.update = (dt) => {
    time += dt
    for (const j of jets) {
      const h = 0.3 + (Math.sin(time * 1.6 + j.userData.phase) * 0.5 + 0.5) * 2.2 // 0.3 ~ 2.5m
      j.scale.y = h
      j.position.y = h / 2
    }
  }
  return g
}
