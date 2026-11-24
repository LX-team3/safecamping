import * as THREE from 'three'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { pointInRing, hitsCollider } from './geo.js'

// ---------- 설정 ----------
const WALK_SPEED = 6   // 걷는 속도 (m/초)
const RUN_SPEED = 10   // Shift 누르고 달리는 속도
const TURN_SPEED = 10  // 몸을 돌리는 빠르기
const JUMP_SPEED = 6   // 스페이스바를 눌렀을 때 위로 뛰어오르는 빠르기 (m/초). 키우면 더 높이 뛰어요
const GRAVITY = 18     // 아래로 끌어당기는 힘. 키우면 더 빨리 떨어져요 (지금 값이면 약 1m 높이로 뛰어요)
const PLAYER_RADIUS = 0.5 // 캐릭터 몸 반지름(m). 부딪힘 계산에 써요
const TURTLE_SCALE = 0.8 // 거북이 크기 (1 = 약 1.8m, 0.8 = 약 1.45m)

// 공식 캐릭터 3D 파일(.glb)이 있으면 public/models/ 에 넣고 경로를 적어요
// 비워 두면('') 기본 거북이가 나와요
const CHARACTER_URL = '' // 예: '/models/character.glb'
const CHARACTER_HEIGHT = 1.6 // 불러온 캐릭터를 이 키(m)에 맞춰 크기를 조절해요

// 3D 파일이 없으면 공식 2D 이미지(배경이 투명한 .png)를 세워서 쓸 수도 있어요
// CHARACTER_URL이 비어 있을 때만 사용해요
const CHARACTER_IMAGE = '' // 예: '/models/character.png'

// 시작 위치 고르기
const START_CLEAR = 1.5 // 건물·나무·텐트에서 이만큼(m) 떨어진 곳에서 시작해요
const START_FAR = 8     // 퀴즈 지점에서 이만큼(m) 떨어진 곳에서 시작해요 (시작하자마자 퀴즈가 열리지 않게)

// 캐릭터가 시작할 빈자리를 찾아요.
// 기준점(QGIS facilities의 type: start 점, 없으면 캠핑장 가운데)에서 시작해서
// 건물·나무 안이면 2m씩 넓혀 가며 둘레를 살펴요
// avoid: 피할 지점들 (퀴즈 위치)
function findStart(camp, avoid = []) {
  const [cx, cz] = camp.start || camp.center
  const colliders = camp.colliders || []
  const ok = (x, z) =>
    pointInRing(x, z, camp.ring) &&
    !hitsCollider(x, z, colliders, START_CLEAR) &&
    avoid.every((p) => Math.hypot(p.x - x, p.z - z) > START_FAR)
  if (ok(cx, cz)) return [cx, cz]
  for (let r = 2; r <= 100; r += 2) {
    for (let k = 0; k < 16; k++) {
      const a = (k / 16) * Math.PI * 2
      const x = cx + Math.cos(a) * r
      const z = cz + Math.sin(a) * r
      if (ok(x, z)) return [x, z]
    }
  }
  return [cx, cz] // 빈자리를 못 찾으면 기준점 그대로
}

// ---------- 두 발로 걷는 거북이 캐릭터 (도형으로 만든 오리지널) ----------
// 앞쪽 = +z. 키는 약 1.6m
function makeTurtle() {
  const m = (color) => new THREE.MeshStandardMaterial({ color, flatShading: true })
  const SKIN = 0x8fd18a
  const holder = new THREE.Group()  // 땅에 놓이는 바깥 그룹 (위치·회전)
  const body = new THREE.Group()    // 걸을 때 위아래로 흔들리는 몸
  holder.add(body)

  // 몸통
  const torso = new THREE.Mesh(new THREE.SphereGeometry(0.42, 12, 10), m(SKIN))
  torso.scale.set(1, 1.25, 0.85)
  torso.position.y = 0.95
  body.add(torso)
  // 배 (앞쪽 연노랑 판)
  const belly = new THREE.Mesh(new THREE.SphereGeometry(0.34, 12, 10), m(0xf0e3b0))
  belly.scale.set(0.9, 1.2, 0.35)
  belly.position.set(0, 0.92, 0.27)
  body.add(belly)
  // 등껍질 (뒤쪽으로 볼록)
  const shell = new THREE.Mesh(new THREE.SphereGeometry(0.42, 10, 6, 0, Math.PI * 2, 0, Math.PI / 2), m(0x3f8f4f))
  shell.rotation.x = -Math.PI / 2
  shell.scale.set(1, 0.6, 1.2)
  shell.position.set(0, 0.97, -0.1)
  body.add(shell)
  // 등껍질 테두리
  const rim = new THREE.Mesh(new THREE.TorusGeometry(0.42, 0.06, 6, 14), m(0xd9b45a))
  rim.scale.set(1, 1.2, 1)
  rim.position.set(0, 0.97, -0.1)
  body.add(rim)
  // 머리
  const head = new THREE.Mesh(new THREE.SphereGeometry(0.3, 12, 10), m(SKIN))
  head.position.set(0, 1.62, 0.06)
  body.add(head)
  for (const x of [-0.11, 0.11]) {
    const eye = new THREE.Mesh(new THREE.SphereGeometry(0.05, 6, 6), m(0x222222))
    eye.position.set(x, 1.68, 0.33)
    body.add(eye)
  }

  // 팔다리: 관절(어깨·엉덩이)을 축으로 앞뒤로 흔들어요
  const limbs = []
  function limb(x, y, len, radius, sign) {
    const joint = new THREE.Group()
    joint.position.set(x, y, 0)
    const mesh = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius * 0.9, len, 8), m(SKIN))
    mesh.position.y = -len / 2 // 관절 아래로 매달기
    joint.add(mesh)
    body.add(joint)
    limbs.push({ joint, sign })
  }
  limb(-0.2, 0.55, 0.5, 0.12, 1)    // 왼다리
  limb(0.2, 0.55, 0.5, 0.12, -1)    // 오른다리
  limb(-0.45, 1.15, 0.45, 0.09, -1) // 왼팔 (다리와 반대로)
  limb(0.45, 1.15, 0.45, 0.09, 1)   // 오른팔

  body.scale.setScalar(TURTLE_SCALE)
  holder.userData.body = body
  holder.userData.limbs = limbs
  return holder
}

// ---------- 3D 파일 캐릭터 불러오기 ----------
// 성공하면 holder 안의 거북이를 불러온 모델로 바꿔요
function loadCharacterFile(holder, url) {
  new GLTFLoader().load(
    url,
    (gltf) => {
      const model = gltf.scene
      // 키를 CHARACTER_HEIGHT에 맞추고 발바닥을 땅(0)에 맞추기
      const box = new THREE.Box3().setFromObject(model)
      const size = box.getSize(new THREE.Vector3())
      const scale = CHARACTER_HEIGHT / (size.y || 1)
      model.scale.setScalar(scale)
      const box2 = new THREE.Box3().setFromObject(model)
      model.position.y -= box2.min.y
      holder.clear()
      holder.add(model)
      holder.userData.limbs = []
      holder.userData.body = null
    },
    undefined,
    (err) => console.warn('캐릭터 파일을 못 불러와서 거북이를 써요:', err)
  )
}

// ---------- 2D 이미지 캐릭터 불러오기 ----------
// 이미지가 항상 카메라를 바라보는 판(스프라이트)으로 세워져요
function loadCharacterImage(holder, url) {
  new THREE.TextureLoader().load(
    url,
    (tex) => {
      tex.colorSpace = THREE.SRGBColorSpace
      const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true }))
      const aspect = tex.image.width / tex.image.height
      sprite.scale.set(CHARACTER_HEIGHT * aspect, CHARACTER_HEIGHT, 1)
      sprite.position.y = CHARACTER_HEIGHT / 2
      holder.clear()
      holder.add(sprite)
      holder.userData.limbs = []
      holder.userData.body = null
      holder.userData.sprite = sprite
    },
    undefined,
    (err) => console.warn('캐릭터 이미지를 못 불러와서 거북이를 써요:', err)
  )
}

// ---------- 플레이어 ----------
// camp: buildCampsite가 돌려준 값 (ring: 캠핑장 테두리, center: 가운데)
export function createPlayer({ scene, camera, controls, terrain, camp }) {
  const turtle = makeTurtle()
  if (CHARACTER_URL) loadCharacterFile(turtle, CHARACTER_URL)
  else if (CHARACTER_IMAGE) loadCharacterImage(turtle, CHARACTER_IMAGE)
  scene.add(turtle)

  controls.minDistance = 6
  controls.maxDistance = 80
  controls.enablePan = false // 화면 끌기로 카메라가 캐릭터에서 벗어나지 않게
  controls.minPolarAngle = 0.2 // 카메라가 캐릭터 바로 위(수직)까지는 못 올라가게

  let jumpY = 0   // 점프로 땅에서 떠 있는 높이 (m). 0이면 땅에 서 있어요
  let jumpV = 0   // 지금 위아래로 움직이는 빠르기 (위쪽이 +)

  // 캐릭터를 시작 위치에 세우고 카메라를 뒤 위쪽에 둬요. 처음과 "다시 하기" 때 불러요
  // avoid: 피할 지점들 (퀴즈 위치)
  function reset(avoid = []) {
    const [sx, sz] = findStart(camp, avoid)
    turtle.position.set(sx, terrain.heightAt(sx, sz), sz)
    turtle.rotation.y = 0
    jumpY = 0
    jumpV = 0
    clearKeys()
    controls.target.copy(turtle.position).add(new THREE.Vector3(0, 1, 0))
    camera.position.copy(controls.target).add(new THREE.Vector3(0, 10, -18))
  }

  // ----- 키보드 -----
  const keys = {}
  const onKeyDown = (e) => {
    keys[e.code] = true
    // 스페이스바: 땅에 서 있을 때만 뛰어요 (공중에서 또 뛰지 못해요)
    if (e.code === 'Space' && api.enabled) {
      e.preventDefault() // 스페이스바로 화면이 내려가거나 버튼이 눌리는 것을 막아요
      if (jumpY === 0) jumpV = JUMP_SPEED
    }
  }
  const onKeyUp = (e) => { keys[e.code] = false }
  // 키를 누른 채 다른 창·탭으로 가면 "키를 뗐다"는 신호를 못 받아요.
  // 그러면 계속 걸어가니까, 창을 벗어날 때 눌린 키를 모두 지워요
  function clearKeys() {
    for (const k in keys) keys[k] = false
  }
  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('keyup', onKeyUp)
  window.addEventListener('blur', clearKeys)
  document.addEventListener('visibilitychange', clearKeys)

  // ----- 매 프레임 -----
  const forward = new THREE.Vector3()
  const right = new THREE.Vector3()
  const move = new THREE.Vector3()
  let walkTime = 0

  // 캐릭터를 정해 준 자리(x, z)로 바로 옮겨요. 카메라도 같이 따라가요 (침수 미션 "다시 대피하기"에서 써요)
  function moveTo(x, z) {
    const old = turtle.position.clone()
    jumpY = 0
    jumpV = 0
    turtle.position.set(x, terrain.heightAt(x, z), z)
    const delta = turtle.position.clone().sub(old)
    camera.position.add(delta)
    controls.target.add(delta)
  }

  // ----- 터치(휴대폰) 입력: 화면의 조이스틱·버튼이 불러요 -----
  const touch = { x: 0, y: 0, run: false } // x: 오른쪽이 +, y: 위(앞으로)가 +. 각각 -1~1
  // 조이스틱을 민 방향과 세기. 손을 떼면 (0, 0)을 보내요
  function setMove(x, y) {
    touch.x = x
    touch.y = y
  }
  // 달리기 버튼을 켜고 끄기
  function setRun(on) {
    touch.run = !!on
  }
  // 점프 버튼: 스페이스바와 똑같이, 땅에 서 있을 때만 뛰어요
  function jump() {
    if (api.enabled && jumpY === 0) jumpV = JUMP_SPEED
  }

  const api = { object: turtle, update, reset, moveTo, setMove, setRun, jump, dispose, enabled: true }

  function update(dt) {
    dt = Math.min(dt, 0.05) // 탭을 다녀오면 dt가 커지는 것 방지

    // 퀴즈 중이거나 게임 시작 전에는 못 움직여요
    if (!api.enabled) {
      clearKeys()
      for (const { joint } of turtle.userData.limbs || []) joint.rotation.x = 0
      if (turtle.userData.body) turtle.userData.body.position.y = 0
      // 뛰는 중에 퀴즈가 열리면 공중에 멈춰 있지 않게 바로 땅에 내려놔요
      jumpY = 0
      jumpV = 0
      turtle.position.y = terrain.heightAt(turtle.position.x, turtle.position.z)
      return
    }

    // 카메라가 캐릭터 둘레를 돈 각도로 앞/옆 구하기
    // (카메라가 "보는 방향"으로 구하면, 위에서 내려다볼 때 앞이 없어져서 키가 안 먹어요)
    const az = controls.getAzimuthalAngle()
    forward.set(-Math.sin(az), 0, -Math.cos(az))
    right.crossVectors(forward, THREE.Object3D.DEFAULT_UP).normalize()

    move.set(0, 0, 0)
    if (keys.KeyW || keys.ArrowUp) move.add(forward)
    if (keys.KeyS || keys.ArrowDown) move.sub(forward)
    if (keys.KeyD || keys.ArrowRight) move.add(right)
    if (keys.KeyA || keys.ArrowLeft) move.sub(right)
    // 터치 조이스틱: 민 방향을 더해요. 살짝 건드린 정도(0.15 아래)는 무시해요
    const push = Math.min(1, Math.hypot(touch.x, touch.y))
    const byTouch = move.lengthSq() === 0 && push > 0.15
    if (byTouch) move.addScaledVector(forward, touch.y).addScaledVector(right, touch.x)

    const moving = move.lengthSq() > 0
    if (moving) {
      move.normalize()
      let speed = keys.ShiftLeft || keys.ShiftRight || touch.run ? RUN_SPEED : WALK_SPEED
      if (byTouch) speed *= Math.max(0.4, push) // 조이스틱을 조금만 밀면 천천히 걸어요
      const old = turtle.position.clone()
      old.y -= jumpY // 점프 높이는 빼고 계산해요 (카메라가 점프를 따라 위아래로 흔들리지 않게)
      let nx = old.x + move.x * speed * dt
      let nz = old.z + move.z * speed * dt

      // 갈 수 있는 곳인지: 캠핑장 안이고, 건물·텐트와 겹치지 않아야 해요
      // (이미 겹쳐 있는 상태면 빠져나갈 수 있게 충돌은 무시해요)
      const colliders = camp.colliders || []
      const stuck = hitsCollider(old.x, old.z, colliders, PLAYER_RADIUS)
      const canGo = (x, z) => pointInRing(x, z, camp.ring) && (stuck || !hitsCollider(x, z, colliders, PLAYER_RADIUS))

      // 막히면 한쪽 방향으로만 미끄러지기 (벽을 따라 비스듬히 걷는 느낌)
      if (!canGo(nx, nz)) {
        if (canGo(nx, old.z)) nz = old.z
        else if (canGo(old.x, nz)) nx = old.x
        else { nx = old.x; nz = old.z }
      }

      turtle.position.set(nx, terrain.heightAt(nx, nz), nz)

      // 가는 방향으로 몸 돌리기
      const want = Math.atan2(move.x, move.z)
      let diff = want - turtle.rotation.y
      diff = Math.atan2(Math.sin(diff), Math.cos(diff)) // -180~180도로
      turtle.rotation.y += diff * Math.min(1, TURN_SPEED * dt)

      // 카메라도 같이 따라가기 (마우스로 돌린 각도는 그대로)
      const delta = turtle.position.clone().sub(old)
      camera.position.add(delta)
      controls.target.add(delta)

      walkTime += dt * speed
    }

    // 점프: 위로 뛰어올랐다가 중력으로 내려와요. 땅에 닿으면 멈춰요.
    // 모양만 뛰는 것이라 건물이나 텐트를 뛰어넘지는 못해요 (부딪힘 계산은 그대로예요)
    if (jumpV !== 0 || jumpY > 0) {
      jumpV -= GRAVITY * dt
      jumpY += jumpV * dt
      if (jumpY <= 0) {
        jumpY = 0
        jumpV = 0
      }
    }
    turtle.position.y = terrain.heightAt(turtle.position.x, turtle.position.z) + jumpY

    // 2D 이미지 캐릭터는 걸을 때 통통 튀게
    const sprite = turtle.userData.sprite
    if (sprite) sprite.position.y = CHARACTER_HEIGHT / 2 + (moving ? Math.abs(Math.sin(walkTime * 2)) * 0.15 : 0)

    // 팔다리 흔들기 + 몸 위아래로 살짝
    const phase = walkTime * 1.6
    const swing = moving ? Math.sin(phase) * 0.6 : 0
    for (const { joint, sign } of turtle.userData.limbs || []) joint.rotation.x = swing * sign
    const body = turtle.userData.body
    if (body) body.position.y = moving ? Math.abs(Math.sin(phase)) * 0.06 : 0
  }

  function dispose() {
    window.removeEventListener('keydown', onKeyDown)
    window.removeEventListener('keyup', onKeyUp)
    window.removeEventListener('blur', clearKeys)
    document.removeEventListener('visibilitychange', clearKeys)
  }

  reset()
  return api
}
