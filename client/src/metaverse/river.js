// 강 (river 레이어: bed 하천 바닥 / water 물길)
import * as THREE from 'three'
import { parts } from './geo.js'
import { patternTexture, flatPolygon } from './shapes.js'

const RIVER = {
  water: 0.25,      // 물 높이 (강바닥 위 m)
}

function bedTexture() {
  return patternTexture((g, n) => {
    g.fillStyle = '#cbbd9c' // 모래·자갈
    g.fillRect(0, 0, n, n)
    g.fillStyle = '#b3a584'
    for (let i = 0; i < 18; i++) g.fillRect((i * 37) % n, (i * 71) % n, 6, 6)
  }, 6)
}

function waterTexture() {
  return patternTexture((g, n) => {
    g.fillStyle = '#3f8fbf'
    g.fillRect(0, 0, n, n)
    g.fillStyle = 'rgba(255,255,255,0.18)' // 잔물결
    g.fillRect(0, n * 0.3, n * 0.6, 3)
    g.fillRect(n * 0.4, n * 0.75, n * 0.5, 3)
  }, 10)
}

// 강 만들기. 돌려주는 값: update(dt) 물결 움직이기
export function buildRiver(river, toWorld, bedLevel, group) {
  const bedMat = new THREE.MeshStandardMaterial({ map: bedTexture(), side: THREE.DoubleSide })
  const waterTex = waterTexture()
  const waterMat = new THREE.MeshStandardMaterial({
    map: waterTex, transparent: true, opacity: 0.9, roughness: 0.25, metalness: 0.1, side: THREE.DoubleSide,
  })

  for (const f of river?.features || []) {
    const type = f.properties.type
    for (const r of parts(f.geometry)) {
      const ring = r.map(toWorld)
      if (type === 'bed') {
        group.add(flatPolygon(ring, bedMat, bedLevel))
      } else if (type === 'water') {
        group.add(flatPolygon(ring, waterMat, bedLevel + RIVER.water))
      } else {
        console.warn('모양이 정해지지 않은 river type:', type)
      }
    }
  }

  return {
    update(dt) {
      waterTex.offset.x += dt * 0.03 // 물이 천천히 흘러가는 것처럼
      waterTex.offset.y += dt * 0.01
    },
  }
}
