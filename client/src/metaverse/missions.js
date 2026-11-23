import * as THREE from 'three'
import { pointInRing, pointAlong, nearestOnSegs, hitsCollider } from './geo.js'
import { disposeTree } from './shapes.js'

const TRIGGER_DIST = 4 // 이 거리(m) 안에 들어가면 퀴즈가 열려요

// ---------- 문제 불러오기 ----------
// PostgreSQL(quiz, quiz_choice) → Spring Boot(GET /api/quizzes) → 여기
// 배포할 때는 .env.production 파일에 VITE_API_BASE=https://(Cloud Run 주소) 를 적어요
const API_BASE = import.meta.env?.VITE_API_BASE ?? ''

export async function loadMissions() {
  try {
    const res = await fetch(`${API_BASE}/api/quizzes`)
    if (!res.ok) throw new Error(`서버 응답 ${res.status}`)
    const list = await res.json()
    if (!Array.isArray(list) || list.length === 0) throw new Error('문제가 0개예요')
    return list
  } catch (err) {
    // 서버가 꺼져 있을 때도 게임은 되도록, 예비 파일(public/metaverse/data/missions.json)을 써요
    console.warn('DB에서 문제를 못 가져와서 예비 파일을 써요:', err.message)
    return (await fetch(`${import.meta.env.BASE_URL}metaverse/data/missions.json`)).json()
  }
}

// fixed: true 인 문제는 항상 넣고, 나머지는 섞어서 n개를 채워요
export function pickMissions(pool, n = 4) {
  const fixed = pool.filter((m) => m.fixed)
  const rest = pool.filter((m) => !m.fixed)
  for (let i = rest.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[rest[i], rest[j]] = [rest[j], rest[i]]
  }
  return [...fixed, ...rest].slice(0, n)
}

// ---------- 미션 위치 찾기 ----------
// anchor 예: { "row": "B", "no": 3 } / { "facility": "water" } / { "stream": 0.5 } / { "road": "3", "t": 0.5 }
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

function findAnchor(anchor, camp) {
  for (const a of Array.isArray(anchor) ? anchor : [anchor]) {
    const p = tryAnchor(a, camp)
    if (!p) continue
    const q = pullInside(p, camp)
    // 시설(건물)에 찍힌 미션만 건물 밖으로 꺼내요. 데크 미션은 데크 위에 그대로 둬요
    return a.facility ? pushOutOfBuildings(q, camp) : q
  }
  console.warn('미션 위치를 못 찾아서 캠핑장 가운데에 둬요:', anchor)
  return { x: camp.center[0], z: camp.center[1] }
}

// ---------- 머리 위 아이콘 (이모지 그림) ----------
function emojiTexture(emoji) {
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

// ---------- 미션 표시와 거리 확인 ----------
export function createMissions({ scene, camp, terrain, missions }) {
  const group = new THREE.Group()
  scene.add(group)

  const list = missions.map((mission) => {
    const { x, z } = findAnchor(mission.anchor, camp)
    const y = terrain.heightAt(x, z)

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

    return { mission, pos: new THREE.Vector3(x, y, z), ring, icon, done: false }
  })

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
    for (const m of list) {
      m.icon.position.y = m.pos.y + 5 + Math.sin(time * 2) * 0.3
      if (!m.done) m.ring.scale.setScalar(1 + Math.sin(time * 4) * 0.08)
    }
    if (!playerPos) {
      arrow.visible = false
      return null
    }

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
    scene.remove(group, arrow)
    disposeTree(group)
    disposeTree(arrow)
  }

  return { list, update, complete, dispose }
}
