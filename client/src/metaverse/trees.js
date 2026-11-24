// 나무: 큰 숲(areas의 forest)과 작은 나무(areas의 small_tree)
import * as THREE from 'three'
import { pointInRing } from './geo.js'
import { mat } from './shapes.js'

// ---------- 숲 (areas 레이어의 type: forest) ----------
export const FOREST = {
  spacing: 6,      // 나무 사이 간격(m). 작을수록 빽빽해요
  margin: 4,       // 캠핑장 테두리에서 이만큼 떨어져서 심어요(m)
  max: 4000,       // 최대 그루 수 (너무 많으면 느려져요)
}

// ---------- 작은 나무 (areas 레이어의 type: small_tree) ----------
// 캠핑장 안 화단·녹지용. 큰 숲보다 작고 밝은 나무
export const SMALL_TREE = {
  spacing: 3.5,         // 나무 사이 간격(m). 줄이면 빽빽하게
  hMin: 2.5,            // 가장 작은 키(m)
  hMax: 4.5,            // 가장 큰 키(m)
  pineRatio: 0.3,       // 원뿔 나무 비율 (나머지는 둥근 나무)
  pineColor: 0x3f7a45,
  broadColor: 0x6aa84f, // 밝은 연두
  max: 1500,
  seed: 777,
  block: 0.45,          // 줄기 둘레에서 캐릭터를 막는 크기(m). 0이면 통과해요
}

// 실행할 때마다 같은 자리에 나무가 나오게 하는 난수
function seededRandom(seed) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

// zones: 나무를 심을 영역들 / avoid: 심지 않을 곳 확인 함수
// opt: 나무 종류별 설정 (간격, 높이, 원뿔 나무 비율, 색). 비워 두면 큰 숲 설정
export function plantForest(zones, heightAt, avoid, opt = {}) {
  const o2 = {
    spacing: FOREST.spacing, max: FOREST.max, hMin: 6, hMax: 13, pineRatio: 0.65,
    pineColor: 0x2f5f3a, broadColor: 0x4f8a3f, seed: 20261117, ...opt,
  }
  const rand = seededRandom(o2.seed)
  const spots = []
  for (const zone of zones) {
    let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity
    for (const [x, z] of zone) {
      minX = Math.min(minX, x); maxX = Math.max(maxX, x)
      minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z)
    }
    for (let x = minX; x < maxX; x += o2.spacing) {
      for (let z = minZ; z < maxZ; z += o2.spacing) {
        // 줄 맞춰 심으면 어색하니 조금씩 흩뜨려요
        const px = x + (rand() - 0.5) * o2.spacing * 0.9
        const pz = z + (rand() - 0.5) * o2.spacing * 0.9
        if (!pointInRing(px, pz, zone) || avoid(px, pz)) continue
        spots.push({ x: px, z: pz, h: o2.hMin + rand() * (o2.hMax - o2.hMin), pine: rand() < o2.pineRatio, turn: rand() * Math.PI })
        if (spots.length >= o2.max) break
      }
    }
  }

  // 같은 모양을 수천 개 그릴 때는 InstancedMesh가 훨씬 빨라요
  const pines = spots.filter((s) => s.pine)
  const broads = spots.filter((s) => !s.pine)
  const trunkGeo = new THREE.CylinderGeometry(0.15, 0.25, 1, 5)
  const pineGeo = new THREE.ConeGeometry(1, 1, 6)
  const broadGeo = new THREE.IcosahedronGeometry(1, 0)
  const trunks = new THREE.InstancedMesh(trunkGeo, mat(0x6b4a2f), spots.length)
  const pineTops = new THREE.InstancedMesh(pineGeo, mat(o2.pineColor), Math.max(1, pines.length))
  const broadTops = new THREE.InstancedMesh(broadGeo, mat(o2.broadColor), Math.max(1, broads.length))
  pineTops.count = pines.length
  broadTops.count = broads.length

  const o = new THREE.Object3D()
  spots.forEach((s, i) => {
    const y = heightAt(s.x, s.z)
    o.position.set(s.x, y + s.h * 0.15, s.z)
    o.rotation.set(0, s.turn, 0)
    o.scale.set(1, s.h * 0.3, 1)
    o.updateMatrix()
    trunks.setMatrixAt(i, o.matrix)
  })
  pines.forEach((s, i) => {
    const y = heightAt(s.x, s.z)
    o.position.set(s.x, y + s.h * 0.62, s.z)
    o.rotation.set(0, s.turn, 0)
    o.scale.set(s.h * 0.28, s.h * 0.78, s.h * 0.28)
    o.updateMatrix()
    pineTops.setMatrixAt(i, o.matrix)
  })
  broads.forEach((s, i) => {
    const y = heightAt(s.x, s.z)
    o.position.set(s.x, y + s.h * 0.6, s.z)
    o.rotation.set(0, s.turn, 0)
    o.scale.set(s.h * 0.32, s.h * 0.3, s.h * 0.32)
    o.updateMatrix()
    broadTops.setMatrixAt(i, o.matrix)
  })
  return { meshes: [trunks, pineTops, broadTops], count: spots.length, spots }
}
