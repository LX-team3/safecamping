// 퀴즈 소품: 문제의 상황을 눈으로 보여 주는 작은 3D 물건들이에요 (모닥불, 버너, 난로, 벌집 …)
// 어떤 문제에 어떤 소품이 나올지는 client/public/metaverse/data/missions.json 의 "prop" 값으로 정해요.
// 새 소품을 만들려면: 아래에 만드는 함수를 쓰고 → 맨 아래 PROPS 표에 이름을 더해요.
//
// 좌표: 소품을 놓는 자리가 (0, 0, 0)이고 y가 위쪽이에요. 크기 단위는 m.
// 잘 보이라고 실제보다 조금 크게 만들었어요 (게임용 크기예요).
import * as THREE from 'three'
import { mat, box, signTexture } from './shapes.js'

// 데크 위에서 소품을 놓는 자리 (데크 가운데 기준 [x, z]).
// 데크에는 이미 텐트(1.2, -1.2)와 테이블(-1.5, 1.8)이 있어서, 그것과 겹치지 않는 곳이에요
const SLOT = {
  open: [-1.7, -1.6],   // 빈 모서리
  tent: [-0.9, -1.1],   // 텐트 바로 옆
  table: [-1.5, 1.8],   // 테이블 위
  tentTop: [1.2, -1.2], // 텐트가 서 있는 자리
}
const DECK_TOP = 0.2    // 데크 자갈 바닥 두께
const TABLE_TOP = 0.6   // 자갈 바닥에서 테이블 윗면까지

// ---------- 모양 도우미 ----------
// 스스로 빛나는 색 (불꽃, 전등). 빛을 받지 않아도 밝게 보여서 밤에도 잘 보여요
const glowCache = new Map()
function glow(color) {
  if (!glowCache.has(color)) glowCache.set(color, new THREE.MeshBasicMaterial({ color }))
  return glowCache.get(color)
}
function put(geo, material, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(geo, material)
  m.position.set(x, y, z)
  return m
}
// 원기둥 (y는 바닥 높이)
function cyl(r, h, color, x = 0, y = 0, z = 0, rTop = r) {
  return put(new THREE.CylinderGeometry(rTop, r, h, 10), mat(color), x, y + h / 2, z)
}
function ball(r, color, x = 0, y = 0, z = 0) {
  return put(new THREE.SphereGeometry(r, 10, 8), mat(color), x, y, z)
}
// 두 점을 잇는 가는 막대 (줄, 전선, 꼬치)
function rod(a, b, r, color) {
  const va = new THREE.Vector3(...a)
  const vb = new THREE.Vector3(...b)
  const m = put(new THREE.CylinderGeometry(r, r, va.distanceTo(vb), 6), mat(color))
  m.position.copy(va).add(vb).multiplyScalar(0.5)
  m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), vb.clone().sub(va).normalize())
  return m
}

// ---------- 같이 쓰는 효과 ----------
// 불꽃: 주황·노랑 원뿔 세 겹이 흔들려요. size: 크기 배율
function flames(ctx, size = 1, x = 0, y = 0, z = 0) {
  const g = new THREE.Group()
  g.position.set(x, y, z)
  // [반지름, 높이, 색, 옆으로 비킨 x, z] 큰 불꽃 하나에 작은 불꽃 둘이 옆에서 삐져나와요
  // (가운데에 겹쳐 두면 안쪽 불꽃이 가려져서 안 보여요)
  const layers = [[0.4, 1.2, 0xff5a1f, 0, 0], [0.26, 0.85, 0xffa02e, 0.24, 0.12], [0.2, 0.6, 0xffe680, -0.16, -0.22]]
  const cones = layers.map(([r, h, color, ox, oz], i) => {
    const c = put(new THREE.ConeGeometry(r * size, h * size, 7), glow(color), ox * size, (h * size) / 2, oz * size)
    c.userData.h = h * size
    c.userData.k = i
    g.add(c)
    return c
  })
  ctx.anim.push((t) => {
    for (const c of cones) {
      const s = 1 + Math.sin(t * 9 + c.userData.k * 2.1) * 0.16
      c.scale.set(1 + Math.sin(t * 7 + c.userData.k) * 0.1, s, 1 + Math.cos(t * 8 + c.userData.k) * 0.1)
      c.position.y = (c.userData.h * s) / 2 // 아래쪽은 붙어 있고 위쪽만 늘었다 줄었다 해요
      c.rotation.y = t * (1.5 + c.userData.k)
    }
  })
  return g
}

// 불빛: 밤에만 주변을 비춰요 (낮에는 티가 안 나서 만들지 않아요)
function fireLight(ctx, power, y = 1, color = 0xff9a3c) {
  if (!ctx.night) return
  const light = new THREE.PointLight(color, power, 28, 1.3)
  light.position.set(0, y, 0)
  ctx.g.add(light)
  ctx.anim.push((t) => { light.intensity = power * (0.85 + Math.sin(t * 11) * 0.1 + Math.sin(t * 5.3) * 0.05) })
}

// 연기: 회색 공이 올라가며 커지고 흐려져요
function smoke(ctx, x = 0, y = 1, z = 0, color = 0x8a8a8a) {
  const puffs = [0, 1, 2].map((i) => {
    const material = new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.3, depthWrite: false })
    ctx.own.push(material) // 흐려지는 정도가 공마다 달라서 재질을 따로 만들어요 (끝나면 지워요)
    const p = put(new THREE.SphereGeometry(0.28, 8, 6), material, x, y, z)
    p.userData.k = i / 3
    ctx.g.add(p)
    return p
  })
  ctx.anim.push((t) => {
    for (const p of puffs) {
      const f = (t * 0.35 + p.userData.k) % 1 // 0 → 1 로 올라가요
      p.position.set(x + Math.sin((f + p.userData.k) * 6) * 0.25, y + f * 2.6, z)
      p.scale.setScalar(0.6 + f * 1.6)
      p.material.opacity = 0.32 * (1 - f)
    }
  })
}

// 화로대: 다리 달린 검은 그릇 + 장작
function firePit(g) {
  g.add(cyl(0.5, 0.22, 0x2d2d2d, 0, 0.28, 0, 0.72)) // 위가 넓은 그릇
  for (const [x, z] of [[0.4, 0.4], [-0.4, 0.4], [0.4, -0.4], [-0.4, -0.4]]) g.add(rod([x, 0, z], [x * 0.6, 0.3, z * 0.6], 0.04, 0x1f1f1f))
  g.add(rod([-0.55, 0.5, -0.15], [0.55, 0.62, 0.15], 0.09, 0x7a4a26))
  g.add(rod([-0.2, 0.5, 0.5], [0.15, 0.66, -0.5], 0.09, 0x6b3f1f))
  g.add(rod([0.45, 0.5, 0.4], [-0.4, 0.7, -0.3], 0.08, 0x8a5a33))
}

// 데크가 아닌 곳에 놓일 때 쓰는 작은 텐트 (데크에는 텐트가 이미 있어요)
function standInTent(g, x, z) {
  const tent = put(new THREE.ConeGeometry(1.8, 2.2, 4), mat(0x3d8bd9), x, 1.1, z)
  tent.rotation.y = Math.PI / 4
  g.add(tent)
}

// ---------- 소품들 ----------
// 모닥불. low: 불이 약해요 / extra: 옆에 같이 놓을 물건
function campfire(ctx, { low = false, extra = null } = {}) {
  const { g } = ctx
  firePit(g)
  g.add(flames(ctx, low ? 0.45 : 1.15, 0, 0.5, 0))
  fireLight(ctx, low ? 9 : 26, 1.2)
  if (!low) smoke(ctx, 0, 1.8, 0)
  if (extra === 'bottle') {
    // 불이 약하다고 붓고 싶어지는 알코올·착화제 통
    g.add(cyl(0.13, 0.42, 0xf2f2f2, 1.0, 0, 0.3))
    g.add(cyl(0.06, 0.12, 0xd93025, 1.0, 0.42, 0.3))
    g.add(box(0.2, 0.16, 0.02, 0xd93025, 1.0, 0.14, 0.44))
  }
  if (extra === 'litter') {
    // 불에 던져 넣으면 안 되는 것들: 스프레이 통, 페트병, 비닐봉지
    g.add(cyl(0.09, 0.36, 0x2e8b57, 0.95, 0, 0.5))
    g.add(cyl(0.05, 0.08, 0xf2f2f2, 0.95, 0.36, 0.5))
    const bottle = cyl(0.1, 0.4, 0x8fd3f4, 1.25, 0, 0.1)
    bottle.rotation.z = Math.PI / 2
    bottle.position.y = 0.1
    g.add(bottle)
    g.add(ball(0.2, 0xffffff, 1.0, 0.16, -0.3))
  }
}

// 숯: 불꽃은 없지만 속은 아직 빨갛게 달아 있어요
function charcoal(ctx) {
  const { g } = ctx
  g.add(cyl(0.5, 0.22, 0x2d2d2d, 0, 0.28, 0, 0.72))
  for (const [x, z] of [[0.4, 0.4], [-0.4, 0.4], [0.4, -0.4], [-0.4, -0.4]]) g.add(rod([x, 0, z], [x * 0.6, 0.3, z * 0.6], 0.04, 0x1f1f1f))
  const hot = []
  const spots = [[0, 0], [0.3, 0.1], [-0.28, 0.15], [0.1, -0.3], [-0.15, -0.25], [0.32, -0.22], [-0.35, -0.1]]
  spots.forEach(([x, z], i) => {
    const isHot = i % 2 === 0
    const material = isHot ? new THREE.MeshBasicMaterial({ color: 0xff3b1f }) : mat(0x3a3a3a)
    if (isHot) ctx.own.push(material)
    const lump = put(new THREE.DodecahedronGeometry(0.14), material, x, 0.56, z)
    if (isHot) hot.push(lump)
    g.add(lump)
  })
  // 빨간 숯이 숨 쉬듯 밝아졌다 어두워져요 ("꺼진 것 같아도 뜨겁다"는 표시)
  ctx.anim.push((t) => {
    hot.forEach((lump, i) => lump.material.color.setHSL(0.02, 1, 0.3 + (Math.sin(t * 2 + i) + 1) * 0.12))
  })
  fireLight(ctx, 5, 0.9, 0xff5a2a)
  smoke(ctx, 0, 0.9, 0, 0xb0b0b0)
}

// 바비큐 그릴과 꼬치
function grill(ctx) {
  const { g } = ctx
  g.add(box(1.1, 0.3, 0.6, 0x8c2f24, 0, 0.6, 0))  // 몸통
  g.add(box(1.14, 0.03, 0.64, 0x9a9a9a, 0, 0.9, 0)) // 석쇠
  for (const [x, z] of [[0.5, 0.25], [-0.5, 0.25], [0.5, -0.25], [-0.5, -0.25]]) g.add(box(0.06, 0.6, 0.06, 0x2d2d2d, x, 0, z))
  g.add(flames(ctx, 0.3, 0.2, 0.9, 0))
  g.add(flames(ctx, 0.25, -0.25, 0.9, 0.05))
  // 꼬치 세 개 (마시멜로)
  for (const x of [-0.35, 0, 0.35]) {
    g.add(rod([x, 0.98, -0.5], [x, 1.02, 0.6], 0.015, 0xc8a070))
    for (const z of [-0.2, 0, 0.2]) g.add(ball(0.07, 0xfff3e0, x, 1.0, z))
  }
  fireLight(ctx, 6, 1.2)
}

// 휴대용 버너 (테이블 위). pan: 불판 반지름 / extra: 옆에 같이 놓을 물건
function burner(ctx, { pan = 0.26, extra = null } = {}) {
  const { g } = ctx
  g.add(box(0.62, 0.12, 0.42, 0x37474f, 0, 0, 0))        // 버너 몸통
  g.add(box(0.2, 0.13, 0.42, 0xb0bec5, 0.31, 0, 0))      // 부탄 캔이 들어가는 칸
  g.add(cyl(0.12, 0.05, 0x222222, -0.08, 0.12, 0))       // 화구
  const flame = put(new THREE.ConeGeometry(0.1, 0.1, 8), glow(0x4fc3f7), -0.08, 0.2, 0)
  g.add(flame)
  ctx.anim.push((t) => flame.scale.setScalar(1 + Math.sin(t * 12) * 0.12))
  g.add(cyl(pan, 0.05, 0x1f1f1f, -0.08, 0.25, 0))        // 불판 (크면 캔 칸까지 덮어요)
  if (extra === 'cans') {
    // 여분의 부탄 캔을 버너 바로 옆에 둔 모습
    for (const [x, z] of [[0.62, 0.1], [0.78, -0.1], [0.66, -0.22]]) {
      g.add(cyl(0.07, 0.22, 0xf5f5f5, x, 0, z))
      g.add(cyl(0.072, 0.07, 0xff7043, x, 0.08, z))
    }
  }
  if (extra === 'spray') {
    // 불 옆에서 쓰면 위험한 모기 스프레이
    g.add(cyl(0.06, 0.28, 0x43a047, -0.6, 0, 0.1))
    g.add(cyl(0.035, 0.06, 0xffffff, -0.6, 0.28, 0.1))
  }
}

// 난로 (텐트 옆). alarm: 일산화탄소 경보기를 같이 세워요
function heater(ctx, { alarm = false } = {}) {
  const { g } = ctx
  g.add(cyl(0.3, 0.08, 0x222222, 0, 0, 0))
  g.add(cyl(0.26, 0.32, 0x607d8b, 0, 0.08, 0))
  const grille = put(new THREE.CylinderGeometry(0.24, 0.24, 0.36, 12), glow(0xff7a2a), 0, 0.58, 0)
  g.add(grille)
  g.add(cyl(0.29, 0.07, 0x37474f, 0, 0.76, 0))
  ctx.anim.push((t) => grille.scale.setScalar(1 + Math.sin(t * 3) * 0.03))
  fireLight(ctx, 6, 0.7, 0xff7a2a)
  if (alarm) {
    // 경보기: 기둥 위 흰 상자, 빨간 불이 깜빡여요
    g.add(cyl(0.03, 1.1, 0x9e9e9e, -0.7, 0, 0.5))
    g.add(box(0.36, 0.26, 0.1, 0xfafafa, -0.7, 1.05, 0.5))
    const led = put(new THREE.SphereGeometry(0.06, 8, 6), new THREE.MeshBasicMaterial({ color: 0xff1744 }), -0.7, 1.24, 0.56)
    ctx.own.push(led.material)
    g.add(led)
    ctx.anim.push((t) => { led.visible = Math.sin(t * 8) > 0 })
  }
}

// 침낭과 담요 (밤에 추울 때 쓰는 것)
function sleepingBag(ctx) {
  const { g } = ctx
  const bag = cyl(0.22, 1.0, 0x3f6fd8, 0, 0, 0)
  bag.rotation.z = Math.PI / 2
  bag.position.set(0, 0.22, 0)
  g.add(bag)
  g.add(box(0.7, 0.14, 0.5, 0xd96c6c, 0.1, 0, 0.7))
  g.add(box(0.7, 0.12, 0.5, 0xf2c14e, 0.1, 0.14, 0.7))
  g.add(cyl(0.1, 0.34, 0xb0bec5, -0.75, 0, 0.5)) // 보온병
}

// 텐트에 붙은 불과 소화기 (텐트 자리 기준)
function tentFire(ctx) {
  const { g } = ctx
  g.add(flames(ctx, 1.3, -0.75, 0.5, 0.75))
  g.add(flames(ctx, 0.8, -0.2, 1.3, 0.2))
  smoke(ctx, -0.4, 2.2, 0.4, 0x555555)
  fireLight(ctx, 30, 1.6)
  extinguisher(g, -2.3, 0, 1.6)
}

// 소화기 하나
function extinguisher(g, x, y, z) {
  g.add(cyl(0.17, 0.62, 0xd32f2f, x, y, z))
  g.add(cyl(0.07, 0.12, 0x212121, x, y + 0.62, z))
  g.add(box(0.26, 0.05, 0.07, 0x212121, x + 0.08, y + 0.74, z))
  g.add(rod([x - 0.06, y + 0.68, z], [x - 0.3, y + 0.25, z], 0.025, 0x212121))
}

// 소화기 보관함 (관리동 앞: 도착하면 위치부터 알아 둬요)
function extinguisherStand(ctx) {
  const { g } = ctx
  g.add(box(1.2, 0.1, 0.5, 0x616161, 0, 0, 0))
  g.add(box(1.2, 1.5, 0.08, 0xfafafa, 0, 0.1, -0.21))
  const sign = put(new THREE.PlaneGeometry(1.0, 0.34), new THREE.MeshBasicMaterial({
    map: signTexture([{ text: '소화기', x: 0.5, size: 80 }], { bg: '#d32f2f', fg: '#ffffff', w: 384, h: 128 }),
  }), 0, 1.38, -0.16)
  ctx.own.push(sign.material)
  g.add(sign)
  extinguisher(g, -0.3, 0.1, 0)
  extinguisher(g, 0.3, 0.1, 0)
}

// 텐트 줄과 팩, 망치 (텐트 옆 기준: 텐트는 +x 쪽에 있어요)
function pegs(ctx) {
  const { g } = ctx
  // 눈에 잘 띄는 노란 줄 두 가닥: 텐트 옆면에서 바닥의 팩까지
  for (const z of [-0.7, 0.9]) {
    g.add(rod([1.3, 1.2, 0], [-0.6, 0.05, z], 0.025, 0xffe14d))
    const peg = rod([-0.72, 0.28, z], [-0.55, -0.02, z], 0.035, 0x9e9e9e)
    g.add(peg)
  }
  // 바닥에 굴러다니는 팩과 망치
  g.add(rod([-0.2, 0.04, 1.5], [0.25, 0.04, 1.7], 0.035, 0x9e9e9e))
  g.add(rod([0.5, 0.04, 1.3], [0.3, 0.04, 0.9], 0.035, 0x9e9e9e))
  g.add(rod([-0.9, 0.06, 1.2], [-0.3, 0.06, 1.0], 0.04, 0x8a5a33))
  g.add(box(0.14, 0.14, 0.34, 0x455a64, -0.93, 0, 1.21))
}

// 랜턴과 손전등 (밤길)
function lantern(ctx) {
  const { g } = ctx
  g.add(cyl(0.2, 0.14, 0x37474f, 0, 0, 0))
  g.add(put(new THREE.CylinderGeometry(0.16, 0.16, 0.34, 10), glow(0xfff2a8), 0, 0.31, 0))
  g.add(cyl(0.2, 0.08, 0x37474f, 0, 0.48, 0))
  const handle = put(new THREE.TorusGeometry(0.17, 0.02, 6, 12, Math.PI), mat(0x212121), 0, 0.56, 0)
  g.add(handle)
  fireLight(ctx, 18, 0.6, 0xfff2a8)
  // 옆에 놓인 손전등과 빛줄기
  const body = cyl(0.07, 0.5, 0x263238, 0, 0, 0)
  body.rotation.z = Math.PI / 2
  body.position.set(0.9, 0.08, 0.4)
  g.add(body)
  const beam = new THREE.Mesh(new THREE.ConeGeometry(0.5, 1.6, 12, 1, true),
    new THREE.MeshBasicMaterial({ color: 0xfff6c0, transparent: true, opacity: 0.25, side: THREE.DoubleSide, depthWrite: false }))
  ctx.own.push(beam.material)
  beam.rotation.z = Math.PI / 2 // 뾰족한 쪽이 손전등, 넓은 쪽이 앞으로
  beam.position.set(1.95, 0.08, 0.4)
  g.add(beam)
}

// 전기 분전함과 물웅덩이
function outlet(ctx) {
  const { g } = ctx
  g.add(cyl(0.06, 0.8, 0x757575, 0, 0, 0))
  g.add(box(0.6, 0.7, 0.28, 0xeceff1, 0, 0.75, 0))
  for (const x of [-0.14, 0.14]) g.add(box(0.16, 0.16, 0.03, 0x263238, x, 1.0, 0.14))
  g.add(box(0.2, 0.2, 0.02, 0xfdd835, 0, 1.22, 0.15)) // 노란 경고 표시
  // 꽂힌 전선
  g.add(rod([0.14, 1.08, 0.16], [0.3, 0.05, 0.6], 0.025, 0x212121))
  g.add(rod([0.3, 0.05, 0.6], [1.2, 0.05, 0.9], 0.025, 0x212121))
  // 물웅덩이와 떨어지는 물방울
  const puddle = put(new THREE.CircleGeometry(0.9, 20), new THREE.MeshBasicMaterial({ color: 0x4fc3f7, transparent: true, opacity: 0.55, depthWrite: false }), 0.3, 0.03, 0.5)
  ctx.own.push(puddle.material)
  puddle.rotation.x = -Math.PI / 2
  g.add(puddle)
  const drop = ball(0.05, 0x4fc3f7, 0.1, 1, 0.35)
  g.add(drop)
  ctx.anim.push((t) => { drop.position.y = 1.0 - ((t * 1.2) % 1) * 0.95 })
}

// 전기 연장선 릴 (줄을 다 풀지 않고 감아 둔 채로 쓰는 모습)
function reel(ctx) {
  const { g } = ctx
  const drum = cyl(0.3, 0.3, 0xff8f00, 0, 0, 0)
  drum.rotation.z = Math.PI / 2
  drum.position.set(0, 0.42, 0)
  g.add(drum)
  for (const x of [-0.18, 0.18]) {
    const side = cyl(0.42, 0.05, 0x212121, 0, 0, 0)
    side.rotation.z = Math.PI / 2
    side.position.set(x, 0.42, 0)
    g.add(side)
  }
  g.add(box(0.5, 0.06, 0.6, 0x212121, 0, 0, 0))
  // 릴에서 나와 텐트 쪽으로 가는 전선과 멀티탭
  g.add(rod([0, 0.2, 0.3], [0.4, 0.04, 0.9], 0.03, 0x212121))
  g.add(rod([0.4, 0.04, 0.9], [1.3, 0.04, 1.0], 0.03, 0x212121))
  g.add(box(0.5, 0.08, 0.16, 0xfafafa, 1.5, 0, 1.0))
  // 감긴 채로 쓰면 뜨거워진다는 표시: 릴 위로 아지랑이
  smoke(ctx, 0, 0.9, 0, 0xffccbc)
}

// 구명조끼와 튜브 (물놀이 전에 챙기는 것)
function lifeJacket(ctx) {
  const { g } = ctx
  g.add(cyl(0.05, 1.5, 0x8d6e63, 0, 0, 0))               // 걸이 기둥
  g.add(rod([-0.5, 1.4, 0], [0.5, 1.4, 0], 0.04, 0x8d6e63))
  g.add(box(0.6, 0.7, 0.2, 0xff6d00, 0, 0.65, 0.05))     // 조끼
  g.add(box(0.62, 0.08, 0.22, 0xfff176, 0, 0.9, 0.05))   // 반사 띠
  g.add(box(0.62, 0.08, 0.22, 0xfff176, 0, 1.1, 0.05))
  const tube = put(new THREE.TorusGeometry(0.42, 0.15, 8, 16), mat(0xff80ab), 1.0, 0.16, 0.4)
  tube.rotation.x = Math.PI / 2
  g.add(tube)
}

// 물에 떠내려가는 공
function floatingBall(ctx) {
  const { g } = ctx
  const water = put(new THREE.CircleGeometry(1.5, 24), new THREE.MeshBasicMaterial({ color: 0x4fc3f7, transparent: true, opacity: 0.45, depthWrite: false }), 0, 0.04, 0)
  ctx.own.push(water.material)
  water.rotation.x = -Math.PI / 2
  g.add(water)
  const b = ball(0.32, 0xef5350, 0, 0.3, 0)
  const stripe = put(new THREE.TorusGeometry(0.32, 0.04, 6, 16), mat(0xffffff))
  b.add(stripe)
  g.add(b)
  // 물결을 따라 천천히 돌며 까딱거려요
  ctx.anim.push((t) => {
    b.position.set(Math.cos(t * 0.6) * 0.8, 0.26 + Math.sin(t * 3) * 0.05, Math.sin(t * 0.6) * 0.8)
    b.rotation.z = t
  })
}

// 물가 주의 표지판과 구명환 (밤 개울가)
function waterSign(ctx) {
  const { g } = ctx
  g.add(cyl(0.05, 1.8, 0x757575, 0, 0, 0))
  const material = new THREE.MeshBasicMaterial({
    map: signTexture([{ text: '물가 주의', x: 0.5, size: 76 }], { bg: '#fdd835', fg: '#212121', w: 384, h: 160 }),
    side: THREE.DoubleSide,
  })
  ctx.own.push(material)
  g.add(put(new THREE.PlaneGeometry(1.2, 0.5), material, 0, 1.7, 0.06))
  const ring = put(new THREE.TorusGeometry(0.3, 0.09, 8, 16), mat(0xe53935), 0, 0.95, 0.08)
  g.add(ring)
  fireLight(ctx, 10, 2, 0xfff2a8)
}

// 캠핑 의자. tilt: 뒤로 기울인 각도
function campChair(g, x, z, color = 0x1e88e5) {
  const c = new THREE.Group()
  c.position.set(x, 0, z)
  c.add(box(0.6, 0.06, 0.55, color, 0, 0.42, 0))
  c.add(box(0.6, 0.6, 0.06, color, 0, 0.45, -0.27))
  for (const [lx, lz] of [[0.26, 0.24], [-0.26, 0.24], [0.26, -0.24], [-0.26, -0.24]]) c.add(box(0.04, 0.42, 0.04, 0x424242, lx, 0, lz))
  g.add(c)
  return c
}

// 한잔한 뒤 물놀이: 의자, 아이스박스, 캔
function drink(ctx) {
  const { g } = ctx
  campChair(g, 0, 0)
  g.add(box(0.7, 0.4, 0.45, 0x1976d2, 0.95, 0, 0.1))
  g.add(box(0.72, 0.08, 0.47, 0xfafafa, 0.95, 0.4, 0.1))
  for (const [x, z] of [[0.8, 0.1], [1.05, 0.15]]) g.add(cyl(0.05, 0.16, 0x9ccc65, x, 0.48, z))
  const fallen = cyl(0.05, 0.16, 0x9ccc65, 0, 0, 0)
  fallen.rotation.z = Math.PI / 2
  fallen.position.set(0.5, 0.05, 0.6)
  g.add(fallen)
}

// 의자를 뒤로 까딱까딱
function rockingChair(ctx) {
  const chair = campChair(ctx.g, 0, 0, 0xe53935)
  ctx.anim.push((t) => { chair.rotation.x = -0.22 - Math.sin(t * 2.2) * 0.14 }) // 뒷다리를 축으로 뒤로 넘어갈 듯
}

// 해먹 (그네처럼 세게 흔들려요)
function hammock(ctx) {
  const { g } = ctx
  for (const x of [-1.4, 1.4]) g.add(cyl(0.08, 1.7, 0x6d4c41, x, 0, 0))
  const swing = new THREE.Group()
  swing.position.y = 1.5 // 기둥 위쪽을 축으로 흔들려요
  const cloth = put(new THREE.TorusGeometry(1.4, 0.32, 6, 14, Math.PI), mat(0xffa726))
  cloth.rotation.x = Math.PI    // 반원을 아래로 처지게
  cloth.scale.set(1, 0.45, 1.6) // 덜 처지고, 폭은 넓게
  swing.add(cloth)
  g.add(swing)
  ctx.anim.push((t) => { swing.rotation.x = Math.sin(t * 2) * 0.5 })
}

// 벌집과 벌
function bees(ctx) {
  const { g } = ctx
  g.add(cyl(0.1, 2.0, 0x6d4c41, 0, 0, 0))
  g.add(rod([0, 1.9, 0], [0.7, 2.1, 0], 0.06, 0x6d4c41))
  // 가지에 매달린 벌집 (납작한 공 세 개)
  for (const [y, r] of [[1.8, 0.2], [1.6, 0.27], [1.38, 0.2]]) {
    const part = ball(r, 0xd9a441, 0.7, y, 0)
    part.scale.y = 0.75
    g.add(part)
  }
  const list = [0, 1, 2, 3, 4, 5].map((i) => {
    const bee = new THREE.Group()
    bee.add(box(0.1, 0.08, 0.14, 0xfdd835, 0, -0.04, 0))
    bee.add(box(0.102, 0.082, 0.04, 0x212121, 0, -0.041, 0))
    bee.userData = { r: 0.5 + (i % 3) * 0.25, s: 1.6 + i * 0.35, y: 1.3 + (i % 4) * 0.2, p: i * 1.1 }
    g.add(bee)
    return bee
  })
  ctx.anim.push((t) => {
    for (const bee of list) {
      const { r, s, y, p } = bee.userData
      bee.position.set(0.7 + Math.cos(t * s + p) * r, y + Math.sin(t * 3 + p) * 0.12, Math.sin(t * s + p) * r)
      bee.rotation.y = -(t * s + p)
    }
  })
}

// 풀숲과 뱀
function snake(ctx) {
  const { g } = ctx
  // 풀 포기
  for (let i = 0; i < 14; i++) {
    const a = i * 2.4
    const r = 0.5 + (i % 4) * 0.25
    g.add(put(new THREE.ConeGeometry(0.1, 0.5 + (i % 3) * 0.15, 4), mat(i % 2 ? 0x4caf50 : 0x66bb6a), Math.cos(a) * r, 0.28, Math.sin(a) * r))
  }
  // 뱀: 공을 한 줄로 이어 붙여 꿈틀거려요
  const body = []
  for (let i = 0; i < 9; i++) {
    const seg = ball(i === 0 ? 0.13 : 0.1 - i * 0.004, i % 2 ? 0x8bc34a : 0x558b2f, 0, 0.1, 0)
    body.push(seg)
    g.add(seg)
  }
  const tongue = box(0.03, 0.02, 0.14, 0xe53935, 0, 0, 0)
  body[0].add(tongue)
  tongue.position.set(0, 0, 0.16)
  ctx.anim.push((t) => {
    body.forEach((seg, i) => {
      seg.position.set(Math.sin(t * 2.5 - i * 0.7) * 0.22, i === 0 ? 0.2 : 0.1, 0.6 - i * 0.17)
    })
    tongue.visible = Math.sin(t * 6) > 0.2
  })
}

// ---------- 소품 표 ----------
// 이름: { slot: 데크 위 자리, make: 만드는 함수 }
// missions.json 의 "prop" 에 이 이름을 적어요
const PROPS = {
  campfire: { slot: 'open', make: (c) => campfire(c) },
  campfire_near_tent: { slot: 'tent', make: (c) => campfire(c) },               // 텐트 가까이 둔 화로대
  campfire_low: { slot: 'open', make: (c) => campfire(c, { low: true, extra: 'bottle' }) },
  campfire_litter: { slot: 'open', make: (c) => campfire(c, { extra: 'litter' }) },
  charcoal: { slot: 'open', make: charcoal },
  charcoal_near_tent: { slot: 'tent', make: charcoal },                          // 텐트 안에 들이려는 숯
  grill: { slot: 'open', make: grill },
  burner: { slot: 'table', make: (c) => burner(c) },
  burner_griddle: { slot: 'table', make: (c) => burner(c, { pan: 0.5 }) },      // 버너보다 큰 불판
  burner_cans: { slot: 'table', make: (c) => burner(c, { extra: 'cans' }) },
  burner_spray: { slot: 'table', make: (c) => burner(c, { extra: 'spray' }) },
  heater: { slot: 'tent', make: (c) => heater(c) },
  co_alarm: { slot: 'tent', make: (c) => heater(c, { alarm: true }) },
  sleeping_bag: { slot: 'tent', make: sleepingBag },
  tent_fire: { slot: 'tentTop', make: tentFire },
  extinguisher: { slot: 'open', make: extinguisherStand },
  pegs: { slot: 'tent', make: pegs },
  lantern: { slot: 'open', make: lantern },
  outlet: { slot: 'open', make: outlet },
  reel: { slot: 'open', make: reel },
  life_jacket: { slot: 'open', make: lifeJacket },
  floating_ball: { slot: 'open', make: floatingBall },
  water_sign: { slot: 'open', make: waterSign },
  drink: { slot: 'open', make: drink },
  rocking_chair: { slot: 'open', make: rockingChair },
  hammock: { slot: 'open', make: hammock },
  bees: { slot: 'open', make: bees },
  snake: { slot: 'open', make: snake },
}

// 소품 하나를 만들어요. 이름이 표에 없으면 null (소품 없이 고리만 떠요)
//   night: 밤이면 불빛을 켜요
//   onDeck: 데크 위에 놓이면 true → 데크의 텐트·테이블 옆 자리에 맞춰 놓아요
// 돌려주는 값: { object, update(dt), dispose() }
export function createProp(name, { night = false, onDeck = false } = {}) {
  const spec = PROPS[name]
  if (!spec) {
    if (name) console.warn('모르는 소품 이름이에요 (props.js의 PROPS 표를 확인하세요):', name)
    return null
  }
  const object = new THREE.Group()
  const g = new THREE.Group()
  object.add(g)
  const ctx = { g, night, anim: [], own: [] }

  const [sx, sz] = onDeck ? SLOT[spec.slot] : [0, 0]
  g.position.set(sx, onDeck ? DECK_TOP : 0, sz)
  if (spec.slot === 'table') {
    g.position.y += TABLE_TOP
    if (!onDeck) { // 데크가 아니면 올려놓을 테이블을 같이 세워요
      object.add(box(1.8, 0.1, 0.8, 0x8a5a33, 0, 0.5, 0), box(0.2, 0.5, 0.6, 0x6b4426, 0, 0, 0))
    }
  }
  // 텐트가 필요한 소품인데 데크가 아니면 작은 텐트를 같이 세워요
  if (!onDeck && spec.slot === 'tentTop') standInTent(object, 0, 0)
  if (!onDeck && spec.slot === 'tent') standInTent(object, SLOT.tentTop[0] - SLOT.tent[0], SLOT.tentTop[1] - SLOT.tent[1])

  spec.make(ctx)

  let time = Math.random() * 10 // 소품마다 움직임이 조금씩 다르게 시작해요
  function update(dt) {
    time += dt
    for (const f of ctx.anim) f(time)
  }
  update(0)

  // 다 쓴 소품 정리. 여러 소품이 같이 쓰는 재질(mat, glow)은 남겨 두고, 모양과 이 소품만의 재질만 지워요
  function dispose() {
    object.traverse((o) => o.geometry?.dispose())
    for (const m of ctx.own) {
      m.map?.dispose()
      m.dispose()
    }
  }

  return { object, update, dispose }
}
