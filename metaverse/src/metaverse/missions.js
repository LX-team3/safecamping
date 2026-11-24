import * as THREE from 'three'
import { pointInRing, pointAlong, nearestOnSegs, hitsCollider } from './geo.js'
import { disposeTree } from './shapes.js'
import { createProp } from './props.js'

const TRIGGER_DIST = 4 // 이 거리(m) 안에 들어가면 퀴즈가 열려요

// 주차장 퀴즈: 캐릭터가 다가오면 주차된 차가 뒤로 살짝 움직여요 (게임용 값이에요)
const BACK_UP = {
  near: 11,   // 이 거리(m) 안으로 오면 움직이기 시작해요
  far: 14,    // 이 거리(m) 밖으로 나가면 제자리로 돌아가요
  dist: 1.0,  // 움직이는 거리(m). 퀴즈가 열리는 거리(4m)보다 안쪽이라 캐릭터에 닿지 않아요
  speed: 1.2, // 움직이는 빠르기 (클수록 빨라요)
}

// ---------- 문제 불러오기 ----------
// 문제 40개는 public/data/missions.json 에 있어요 (DB는 쓰지 않기로 했어요)
// 문제마다 audience(child 아이 / adult 어른), time(day 낮 / night 밤)이 적혀 있어요
export async function loadMissions() {
  return (await fetch('/data/missions.json')).json()
}

// 다음에 낼 문제 하나를 무작위로 골라요
//   audience: 'child' 또는 'adult', time: 'day' 또는 'night'
//   solved: 이미 푼 문제 id 모음(Set)
// 낮에는 낮 문제만, 밤에는 밤 문제만 나와요. 아이·어른 문제도 섞이지 않아요
export function pickNextMission(pool, { audience, time, solved }) {
  // 고른 대상 + 지금 시간대에 맞는 문제들 (10개)
  const group = pool.filter((m) => m.audience === audience && m.time === time)
  let list = group.filter((m) => !solved.has(m.id))
  // 이 묶음을 다 풀었으면, 이 묶음의 푼 기록만 지우고 처음부터 다시 내요
  if (list.length === 0) {
    for (const m of group) solved.delete(m.id)
    list = group
  }
  if (list.length === 0) list = pool // 파일에 맞는 문제가 하나도 없을 때를 대비해요
  return list[Math.floor(Math.random() * list.length)]
}

// ---------- 미션 위치 찾기 ----------
// anchor 예: { "row": "B", "no": 3 } / { "facility": "water" } / { "stream": 0.5 } / { "road": "3", "t": 0.5 } / { "tree": true }
// 배열로 여러 개 적으면 앞에서부터 찾아서, 처음 찾은 곳을 써요 (예: 놀이터가 없으면 다음 후보)
function tryAnchor(anchor, camp) {
  const kids = camp.group.children
  if (anchor.row) {
    const o = kids.find((k) => k.userData.row === anchor.row && k.userData.no === anchor.no)
      || kids.find((k) => k.userData.row === anchor.row) // 번호가 없으면 그 줄 첫 데크
    return o ? { x: o.position.x, z: o.position.z } : null
  }
  if (anchor.facility) {
    const o = kids.find((k) => k.userData.type === anchor.facility)
    return o ? { x: o.position.x, z: o.position.z } : null
  }
  // { "car": true } → 달리는 차 한 대를 무작위로 골라요. follow에 그 차를 담아 두면
  // 고리와 아이콘이 매 프레임 차를 따라가요 (차는 QGIS car_routes 선을 따라 달려요)
  if (anchor.car) {
    const cars = camp.carObjects || []
    if (!cars.length) return null
    const car = cars[Math.floor(Math.random() * cars.length)]
    return { x: car.position.x, z: car.position.z, follow: car }
  }
  // { "parked": true } → 주차된 차(buildings 레이어의 type: car) 한 대를 무작위로 골라요.
  // actor에 그 차를 담아 두면, 캐릭터가 다가올 때 차가 뒤로 살짝 움직여요 (아래 createMissions의 backUp)
  if (anchor.parked) {
    const cars = kids.filter((k) => k.userData.building && k.userData.type === 'car'
      && (!camp.ring || pointInRing(k.position.x, k.position.z, camp.ring)))
    if (!cars.length) return null
    const car = cars[Math.floor(Math.random() * cars.length)]
    return { x: car.position.x, z: car.position.z, actor: car }
  }
  // { "tree": true } → 캠핑장 안의 작은 나무 하나를 무작위로 골라, 그 나무 바로 옆에 둬요
  // (나무는 QGIS areas 레이어의 small_tree 영역에 심긴 것이에요. 좌표를 코드에 적지 않아요)
  if (anchor.tree) {
    // 나무를 무작위 순서로 살펴보고, 그 둘레(2.5m)에서 "풀밭인 자리"를 처음 찾으면 그곳을 써요.
    // 풀밭인 자리 = 캠핑장 안 + 데크·카라반·주차칸에서 6m 넘게 떨어짐 + 건물·텐트·다른 나무와 안 겹침
    // (데크 바로 옆 나무를 고르면 문제가 데크 위로 올라가 버려서, 그런 자리는 건너뛰어요)
    const spots = [...(camp.treeSpots || [])].sort(() => Math.random() - 0.5)
    const sites = kids.filter((k) => k.userData.row || k.userData.type === 'deck')
    const start = Math.random() * Math.PI * 2
    for (const t of spots) {
      for (let i = 0; i < 8; i++) {
        const a = start + (i * Math.PI) / 4
        const x = t.x + Math.cos(a) * 2.5
        const z = t.z + Math.sin(a) * 2.5
        if (camp.ring && !pointInRing(x, z, camp.ring)) continue
        if (sites.some((s) => Math.hypot(s.position.x - x, s.position.z - z) < 6)) continue
        if (hitsCollider(x, z, camp.colliders || [], 1.5)) continue
        return { x, z }
      }
    }
    return null // 알맞은 나무가 없으면 다음 후보(데크 번호 등)로 넘어가요
  }
  if (anchor.stream !== undefined && camp.streamLines.length) {
    const { x, z } = pointAlong(camp.streamLines[0], anchor.stream)
    return { x, z }
  }
  if (anchor.road !== undefined) {
    const r = camp.roadLines.find((l) => l.name === String(anchor.road))
    if (!r) return null
    const { x, z } = pointAlong(r.pts, anchor.t ?? 0.5)
    return { x, z }
  }
  return null
}

// 테두리 밖(개울, 놀이터 등)에 있는 미션은 캐릭터가 갈 수 없으니
// 가장 가까운 테두리 안쪽 3m 지점으로 당겨요
function pullInside(p, camp) {
  const ring = camp.ring
  if (!ring || pointInRing(p.x, p.z, ring)) return p
  const best = nearestOnSegs(p.x, p.z, ring, true) // 가장 가까운 테두리 위의 점
  // 테두리 점에서 캠핑장 가운데 쪽으로 3m 들어가기
  const [cx, cz] = camp.center
  const l = Math.hypot(cx - best.x, cz - best.z) || 1
  return { x: best.x + ((cx - best.x) / l) * 3, z: best.z + ((cz - best.z) / l) * 3 }
}

// 관리동처럼 건물 한가운데 찍힌 미션은 들어갈 수 없으니
// 캠핑장 가운데 쪽으로 1m씩 옮겨서 건물 밖으로 꺼내요 (건물 앞마당에 놓여요)
function pushOutOfBuildings(p, camp) {
  const colliders = camp.colliders || []
  if (!hitsCollider(p.x, p.z, colliders, 1)) return p
  const [cx, cz] = camp.center
  const l = Math.hypot(cx - p.x, cz - p.z) || 1
  const ux = (cx - p.x) / l
  const uz = (cz - p.z) / l
  for (let d = 1; d < 60; d++) {
    const x = p.x + ux * d
    const z = p.z + uz * d
    if (!hitsCollider(x, z, colliders, 1.5)) return { x: x + ux * 1.5, z: z + uz * 1.5 }
  }
  return p
}

// 문제가 뜰 위치(x, z)를 찾아요.
// 1) QGIS 점 레이어(quiz_points)에 문제의 place와 같은 값의 점이 있으면, 그중 하나를 무작위로 골라요
//    (같은 종류의 점을 여러 개 찍어 두면 판마다 다른 자리에 떠요)
// 2) 그런 점이 없으면 문제에 적힌 임시 위치(anchor: 데크 번호, 시설, 개울 등)를 써요
// avoid: 캐릭터 위치. 고를 수 있으면 minDist(m)보다 먼 점을 골라요 (발밑에 갑자기 뜨지 않게)
export function findMissionPos(mission, camp, avoid = null, minDist = 0) {
  let spots = (camp.quizPoints || []).filter((p) => p.place === mission.place)
  if (spots.length) {
    if (avoid) {
      const far = spots.filter((p) => Math.hypot(p.x - avoid.x, p.z - avoid.z) > minDist)
      if (far.length) spots = far
    }
    const p = spots[Math.floor(Math.random() * spots.length)]
    // 점을 테두리 밖이나 건물 안에 찍었어도 캐릭터가 갈 수 있는 곳으로 살짝 옮겨요
    return pushOutOfBuildings(pullInside({ x: p.x, z: p.z }, camp), camp)
  }
  return findAnchor(mission.anchor, camp)
}

function findAnchor(anchor, camp) {
  for (const a of Array.isArray(anchor) ? anchor : [anchor]) {
    const p = tryAnchor(a, camp)
    if (!p) continue
    if (p.follow || p.actor) return p // 차에 붙는 문제는 자리를 고치지 않고 차 위치 그대로 써요
    const q = pullInside(p, camp)
    // 시설(건물)에 찍힌 미션만 건물 밖으로 꺼내요. 데크 미션은 데크 위에 그대로 둬요
    return a.facility ? pushOutOfBuildings(q, camp) : q
  }
  console.warn('미션 위치를 못 찾아서 캠핑장 가운데에 둬요:', anchor)
  return { x: camp.center[0], z: camp.center[1] }
}

// ---------- 머리 위 아이콘 (이모지 그림) ----------
export function emojiTexture(emoji) {
  const c = document.createElement('canvas')
  c.width = c.height = 128
  const g = c.getContext('2d')
  g.fillStyle = 'rgba(255,255,255,0.92)'
  g.beginPath()
  g.arc(64, 64, 60, 0, Math.PI * 2)
  g.fill()
  g.font = '76px sans-serif'
  g.textAlign = 'center'
  g.textBaseline = 'middle'
  g.fillText(emoji, 64, 70)
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  return tex
}

// ---------- 소품 놓기 ----------
// 문제 자리(x, z)가 데크 한가운데면 그 데크를 돌려줘요 (없으면 null).
// 데크 위에는 텐트와 테이블이 있어서, 소품을 그 옆 빈자리에 데크 방향대로 놓으려고 찾아요
function deckAt(x, z, camp) {
  return camp.group.children.find((k) => {
    const u = k.userData
    const isDeck = u.type === 'deck' || (u.row && u.row !== 'P' && u.row !== 'F' && (!u.siteType || u.siteType === 'deck'))
    return isDeck && Math.hypot(k.position.x - x, k.position.z - z) < 1
  }) || null
}

// 문제에 적힌 소품(mission.prop)을 문제 자리에 세워요. 소품이 없는 문제면 null
function placeProp(mission, x, y, z, camp, night) {
  if (!mission.prop) return null
  const deck = deckAt(x, z, camp)
  const prop = createProp(mission.prop, { night, onDeck: !!deck })
  if (!prop) return null
  if (deck) {
    // 데크와 같은 자리, 같은 방향 (sites 폴리곤 데크는 방향이 userData.angle 에 있어요)
    prop.object.position.copy(deck.position)
    prop.object.rotation.y = deck.userData.angle ?? deck.rotation.y
  } else {
    prop.object.position.set(x, y, z)
  }
  return prop
}

// ---------- 미션 표시와 거리 확인 ----------
// positions: 문제마다 미리 정해 둔 위치 목록(없으면 여기서 찾아요). 위치를 무작위로 고르기 때문에,
// 밖에서 한 번 고른 자리를 그대로 쓰려고 받아요
// night: 밤이면 true (소품의 불빛을 켜요)
export function createMissions({ scene, camp, terrain, missions, positions = [], night = false }) {
  const group = new THREE.Group()
  scene.add(group)
  // 소품은 따로 모아요. 여러 소품이 같은 재질을 나눠 써서, 정리하는 방법이 고리·아이콘과 달라요
  const propGroup = new THREE.Group()
  scene.add(propGroup)
  const props = []

  const list = missions.map((mission, i) => {
    const { x, z, follow = null, actor = null } = positions[i] || findMissionPos(mission, camp)
    const y = terrain.heightAt(x, z)

    const prop = placeProp(mission, x, y, z, camp, night)
    if (prop) {
      propGroup.add(prop.object)
      props.push(prop)
    }

    // 바닥 고리
    const ringGeo = new THREE.RingGeometry(2.6, 3.2, 32)
    ringGeo.rotateX(-Math.PI / 2)
    const ring = new THREE.Mesh(ringGeo, new THREE.MeshBasicMaterial({
      color: 0xffc83d, transparent: true, opacity: 0.85,
      polygonOffset: true, polygonOffsetFactor: -4, polygonOffsetUnits: -4,
    }))
    ring.position.set(x, y + 0.4, z)
    // 떠 있는 아이콘
    const icon = new THREE.Sprite(new THREE.SpriteMaterial({ map: emojiTexture(mission.icon) }))
    icon.scale.set(3, 3, 1)
    icon.position.set(x, y + 5, z)
    group.add(ring, icon)

    // follow: 따라다닐 물체(달리는 차). 없으면 한자리에 가만히 있어요
    // backUp: 뒤로 움직일 주차된 차 (없으면 null)
    // prop: 소품 물체 (없으면 null). 미니게임이 그릴 자리를 찾을 때 써요
    return { mission, pos: new THREE.Vector3(x, y, z), ring, icon, done: false, follow, backUp: actor ? makeBackUp(actor) : null, prop: prop?.object ?? null }
  })

  // 주차된 차를 뒤로 움직일 준비. 차의 원래 자리와 길이 방향을 적어 둬요
  function makeBackUp(obj) {
    const car = obj.children[0]                  // 안쪽 묶음: 차 길이 방향으로 돌려져 있어요 (buildings.js의 makeCar)
    const body = car.children[0].geometry.parameters // 차체 상자 크기 { width, depth(길이) }
    const a = car.rotation.y
    return { obj, car, home: obj.position.clone(), ux: Math.sin(a), uz: Math.cos(a), w: body.width, len: body.depth, dir: 0, off: 0, lights: null }
  }

  // 후진등: 움직이는 쪽 끝에 빨간 등 두 개와 흰 등 하나
  function addBackLights(b) {
    const g = new THREE.Group()
    const z = b.dir * (b.len / 2 + 0.02)
    for (const sx of [-1, 1]) {
      const red = new THREE.Mesh(new THREE.BoxGeometry(0.32, 0.16, 0.05), new THREE.MeshBasicMaterial({ color: 0xff2d2d }))
      red.position.set(sx * b.w * 0.34, 0.8, z)
      g.add(red)
    }
    const white = new THREE.Mesh(new THREE.BoxGeometry(0.22, 0.12, 0.05), new THREE.MeshBasicMaterial({ color: 0xffffff }))
    white.position.set(0, 0.62, z)
    g.add(white)
    b.car.add(g)
    b.lights = g
  }

  // 매 프레임: 캐릭터가 가까우면 차를 뒤로, 멀어지면 제자리로
  function updateBackUp(b, dt, playerPos) {
    const d = Math.hypot(playerPos.x - b.home.x, playerPos.z - b.home.z)
    if (!b.dir && d < BACK_UP.near) {
      // 어느 쪽으로 후진할지 정해요.
      // 1) 차 앞뒤 중 한쪽만 건물·다른 차로 막혀 있으면, 뚫린 쪽으로 나와요 (벽에 대고 세운 차가 벽 속으로 들어가지 않게)
      // 2) 둘 다 뚫려 있으면(또는 둘 다 막혀 있으면) 캐릭터가 있는 쪽으로 나와요
      const blocked = (sign) => {
        const d2 = b.len / 2 + 1.5 // 차 끝에서 1.5m 더 나간 자리
        return hitsCollider(b.home.x + b.ux * sign * d2, b.home.z + b.uz * sign * d2, camp.colliders || [], 0.5)
      }
      const front = blocked(1)
      const back = blocked(-1)
      if (front !== back) b.dir = front ? -1 : 1
      else b.dir = (playerPos.x - b.home.x) * b.ux + (playerPos.z - b.home.z) * b.uz >= 0 ? 1 : -1
      addBackLights(b)
    }
    if (!b.dir) return
    const goal = d < BACK_UP.far ? BACK_UP.dist : 0
    b.off += (goal - b.off) * Math.min(1, dt * BACK_UP.speed)
    b.obj.position.set(b.home.x + b.ux * b.dir * b.off, b.home.y, b.home.z + b.uz * b.dir * b.off)
    b.lights.visible = goal > 0 && Math.sin(time * 6) > -0.3 // 움직이는 동안 깜빡여요
    if (goal === 0 && b.off < 0.02) resetBackUp(b) // 제자리에 돌아왔으면 처음 상태로
  }

  // 차를 원래 자리로 돌려놓고 후진등을 떼요
  function resetBackUp(b) {
    b.obj.position.copy(b.home)
    if (b.lights) {
      b.car.remove(b.lights)
      disposeTree(b.lights)
      b.lights = null
    }
    b.dir = 0
    b.off = 0
  }

  // 캐릭터 머리 위 화살표 (가장 가까운 미션을 가리켜요)
  const arrow = new THREE.Group()
  const cone = new THREE.Mesh(new THREE.ConeGeometry(0.35, 1, 4), new THREE.MeshBasicMaterial({ color: 0xff5a36 }))
  cone.rotation.x = Math.PI / 2 // 뾰족한 쪽이 +z를 향하게
  arrow.add(cone)
  scene.add(arrow)

  let open = null // 지금 열려 있는 미션
  let time = 0

  // 매 프레임: 아이콘 움직이기, 화살표 돌리기, 가까이 왔는지 확인
  // 새로 도착한 미션이 있으면 돌려줘요
  function update(dt, playerPos) {
    time += dt
    for (const p of props) p.update(dt) // 불꽃 흔들림 같은 소품 움직임
    for (const m of list) {
      if (m.follow) {
        // 차를 따라가요: 문제 자리(pos)를 차 위치로 옮기고, 고리와 아이콘도 같이 옮겨요
        const p = m.follow.position
        m.pos.set(p.x, terrain.heightAt(p.x, p.z), p.z)
        m.ring.position.set(p.x, m.pos.y + 0.4, p.z)
        m.icon.position.set(p.x, 0, p.z)
      }
      m.icon.position.y = m.pos.y + 5 + Math.sin(time * 2) * 0.3
      if (!m.done) m.ring.scale.setScalar(1 + Math.sin(time * 4) * 0.08)
    }
    if (!playerPos) {
      arrow.visible = false
      return null
    }
    for (const m of list) if (m.backUp) updateBackUp(m.backUp, dt, playerPos)

    // 가장 가까운 남은 미션
    let near = null
    let best = Infinity
    for (const m of list) {
      if (m.done) continue
      const d = Math.hypot(m.pos.x - playerPos.x, m.pos.z - playerPos.z)
      if (d < best) { best = d; near = m }
    }

    arrow.visible = !!near && !open
    if (near) {
      arrow.position.set(playerPos.x, playerPos.y + 2.4, playerPos.z)
      arrow.rotation.y = Math.atan2(near.pos.x - playerPos.x, near.pos.z - playerPos.z)
    }

    if (near && !open && best < TRIGGER_DIST) {
      open = near
      return near.mission
    }
    return null
  }

  // 미션을 맞혔을 때
  function complete(id) {
    const m = list.find((x) => x.mission.id === id)
    if (m) {
      m.done = true
      m.ring.material.color.set(0x4caf50)
      m.ring.scale.setScalar(1)
      m.icon.material.map.dispose() // 예전 아이콘 그림은 메모리에서 지워요
      m.icon.material.map = emojiTexture('✅')
    }
    open = null
  }

  // 화면에서 빼고, 쓰던 그래픽 메모리도 돌려줘요 ("다시 하기" 때 새 문제로 바꿀 때 불러요)
  function dispose() {
    scene.remove(group, arrow, propGroup)
    disposeTree(group)
    disposeTree(arrow)
    for (const p of props) p.dispose()
    for (const m of list) if (m.backUp) resetBackUp(m.backUp) // 뒤로 나와 있던 차를 제자리로
  }

  return { list, update, complete, dispose }
}
