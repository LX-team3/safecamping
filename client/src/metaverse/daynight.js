// 낮·밤 바꾸기: 하늘색, 안개, 빛, 별과 달, 캐릭터 손전등
import * as THREE from 'three'

// 숫자를 바꾸면 분위기가 바뀌어요
const MODES = {
  day: {
    sky: 0xbfe3f5,                                  // 하늘·안개 색
    hemi: { sky: 0xffffff, ground: 0x88aa66, power: 1.2 }, // 하늘에서 고르게 내려오는 빛
    sun: { color: 0xffffff, power: 2, pos: [500, 1000, 300] }, // 해
    lamp: 0,                                        // 캐릭터 손전등 밝기 (낮에는 꺼요)
  },
  night: {
    sky: 0x0b1530,
    hemi: { sky: 0x6f86c9, ground: 0x1a2430, power: 0.45 },
    sun: { color: 0xa9c1ff, power: 0.5, pos: [-600, 900, -400] }, // 달빛
    lamp: 28,
  },
}

const STAR_COUNT = 700
const SKY_RADIUS = 6000 // 별과 달까지의 거리 (m)

// 밤하늘 별: 위쪽 반구에 흩뿌린 점들
function makeStars() {
  const pos = []
  for (let i = 0; i < STAR_COUNT; i++) {
    const a = Math.random() * Math.PI * 2          // 방향
    const h = 0.08 + Math.random() * 0.92          // 높이 (0 = 지평선, 1 = 머리 위)
    const r = Math.sqrt(1 - h * h)
    pos.push(Math.cos(a) * r * SKY_RADIUS, h * SKY_RADIUS, Math.sin(a) * r * SKY_RADIUS)
  }
  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3))
  // fog: false → 멀리 있어도 안개에 가려지지 않아요. sizeAttenuation: false → 거리와 상관없이 같은 크기
  return new THREE.Points(geo, new THREE.PointsMaterial({ color: 0xffffff, size: 2, sizeAttenuation: false, fog: false }))
}

function makeMoon(dir) {
  const moon = new THREE.Mesh(
    new THREE.SphereGeometry(220, 20, 14),
    new THREE.MeshBasicMaterial({ color: 0xfff6d8, fog: false }),
  )
  moon.position.copy(dir).multiplyScalar(SKY_RADIUS)
  return moon
}

// hemi: 하늘빛(HemisphereLight), sun: 해(DirectionalLight) — scene.js에서 만든 것을 받아서 조절해요
export function createDayNight({ scene, hemi, sun }) {
  const nightSky = new THREE.Group() // 별 + 달 (밤에만 보여요)
  nightSky.add(makeStars(), makeMoon(new THREE.Vector3(...MODES.night.sun.pos).normalize()))
  nightSky.visible = false
  scene.add(nightSky)

  // 캐릭터가 들고 다니는 손전등. 밤에 주변이 보이게 해요
  const lamp = new THREE.PointLight(0xffd9a0, 0, 45, 1.2)
  lamp.position.set(0, 3, 0)

  let mode = 'day'

  return {
    get mode() { return mode },
    // 손전등을 캐릭터에 달아요
    attachLamp(obj) { obj.add(lamp) },
    // name: 'day' 또는 'night'
    set(name) {
      const m = MODES[name]
      if (!m) return
      mode = name
      scene.background.set(m.sky)
      scene.fog.color.set(m.sky)
      hemi.color.set(m.hemi.sky)
      hemi.groundColor.set(m.hemi.ground)
      hemi.intensity = m.hemi.power
      sun.color.set(m.sun.color)
      sun.intensity = m.sun.power
      sun.position.set(...m.sun.pos)
      lamp.intensity = m.lamp
      nightSky.visible = name === 'night'
    },
  }
}
