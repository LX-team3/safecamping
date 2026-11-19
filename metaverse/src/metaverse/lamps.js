// 가로등 (facilities 레이어에서 type이 lamp인 점)
// 낮에는 꺼져 있고, 밤 버전에서 불이 켜져요
import * as THREE from 'three'
import { box } from './shapes.js'

const LAMP = {
  height: 4.5,       // 기둥 높이 (m)
  pole: 0x3b4046,    // 기둥 색
  off: 0xd8d8d0,     // 꺼진 전구 색
  on: 0xffe7a8,      // 켜진 전구 색 (따뜻한 노란빛)
  power: 60,         // 불빛 세기
  reach: 28,         // 불빛이 닿는 거리 (m)
  glow: 7,           // 바닥에 비치는 빛 동그라미 반지름 (m)
  maxLights: 4,      // 진짜 불빛을 내는 가로등 수. 불빛 계산이 무거워서 캐릭터와 가까운 것만 켜요
  block: 0.25,       // 기둥 충돌 상자 크기 (m)
}

// 바닥에 비치는 빛: 가운데가 밝고 가장자리로 갈수록 흐려지는 동그라미 그림
function glowTexture() {
  const c = document.createElement('canvas')
  c.width = c.height = 128
  const g = c.getContext('2d')
  const grad = g.createRadialGradient(64, 64, 0, 64, 64, 64)
  grad.addColorStop(0, 'rgba(255, 226, 150, 0.55)')
  grad.addColorStop(1, 'rgba(255, 226, 150, 0)')
  g.fillStyle = grad
  g.fillRect(0, 0, 128, 128)
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  return tex
}

// spots: 가로등 위치 목록 [[x, z], ...]
// 돌려주는 값: setNight(true/false) 불 켜고 끄기, update(who) 캐릭터와 가까운 가로등에 불빛 옮기기
export function buildLamps(spots, heightAt, group, colliders = []) {
  // 전구와 바닥 빛은 모든 가로등이 같은 재질을 같이 써요 (한 번에 켜고 끄기 쉬워요)
  const bulbMat = new THREE.MeshStandardMaterial({ color: LAMP.off, emissive: LAMP.on, emissiveIntensity: 0 })
  const bulbGeo = new THREE.SphereGeometry(0.28, 12, 8)
  const glowMat = new THREE.MeshBasicMaterial({
    map: glowTexture(), transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    polygonOffset: true, polygonOffsetFactor: -4, polygonOffsetUnits: -4, // 바닥과 겹쳐 깜빡이지 않게
  })
  const glowGeo = new THREE.CircleGeometry(LAMP.glow, 24)
  glowGeo.rotateX(-Math.PI / 2) // 바닥에 눕히기

  const lamps = [] // { x, y, z }
  const glows = []
  for (const [x, z] of spots) {
    const y = heightAt(x, z)
    const g = new THREE.Group()
    g.add(box(0.5, 0.25, 0.5, LAMP.pole))                              // 받침
    g.add(box(0.16, LAMP.height, 0.16, LAMP.pole, 0, 0.25))            // 기둥
    g.add(box(0.7, 0.1, 0.7, LAMP.pole, 0, LAMP.height + 0.55))        // 갓
    const bulb = new THREE.Mesh(bulbGeo, bulbMat)                       // 전구
    bulb.position.y = LAMP.height + 0.3
    g.add(bulb)
    const glow = new THREE.Mesh(glowGeo, glowMat)                       // 바닥에 비치는 빛
    glow.position.y = 0.4 // 도로(+0.25m)보다 위
    glow.visible = false
    g.add(glow)
    glows.push(glow)
    g.position.set(x, y, z)
    g.userData = { type: 'lamp' }
    group.add(g)
    lamps.push({ x, y, z })
    colliders.push({ x, z, hw: LAMP.block, hd: LAMP.block, angle: 0 }) // 기둥은 통과하지 못해요
  }

  // 진짜 불빛 몇 개를 만들어 두고, 캐릭터와 가까운 가로등으로 옮겨 가며 써요
  const lights = []
  for (let i = 0; i < Math.min(LAMP.maxLights, lamps.length); i++) {
    const light = new THREE.PointLight(LAMP.on, 0, LAMP.reach, 1.2)
    group.add(light)
    lights.push(light)
  }

  let night = false
  let lastKey = ''

  function place(who) {
    // 캐릭터와 가까운 순서로 줄 세워서, 앞에서부터 불빛을 하나씩 놓아요
    const near = lamps
      .map((l, i) => ({ i, d: who ? Math.hypot(l.x - who.x, l.z - who.z) : i }))
      .sort((a, b) => a.d - b.d)
      .slice(0, lights.length)
    const key = near.map((n) => n.i).join(',')
    if (key === lastKey) return // 가까운 가로등이 그대로면 옮길 필요 없어요
    lastKey = key
    near.forEach((n, k) => {
      const l = lamps[n.i]
      lights[k].position.set(l.x, l.y + LAMP.height, l.z)
    })
  }
  place(null)

  return {
    count: lamps.length,
    setNight(on) {
      night = on
      bulbMat.emissiveIntensity = on ? 1.5 : 0
      for (const g of glows) g.visible = on
      for (const l of lights) l.intensity = on ? LAMP.power : 0
    },
    update(who) {
      if (night && lamps.length > lights.length) place(who)
    },
  }
}
