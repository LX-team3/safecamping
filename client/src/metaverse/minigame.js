// 퀴즈 앞에 잠깐 하는 미니게임이에요. 지금은 "마시멜로 굽기" 하나예요.
// 문제(missions.json)에 "minigame": "marshmallow" 가 적혀 있으면, 고리에 닿았을 때 퀴즈 대신 이 게임이 먼저 열려요.
// 캐릭터 눈높이(1인칭)로 그릴을 내려다보고, Space나 마우스 클릭으로 마시멜로를 뒤집어요.
// 시간이 다 되면 "앗 뜨거!" 장면이 잠깐 나오고 퀴즈로 넘어가요.
import * as THREE from 'three'

// ---------- 설정 (모두 게임용 값이에요. 실제 굽는 시간이 아니에요) ----------
const PLAY_SECONDS = 10   // 굽는 시간(초). 이 시간이 지나면 퀴즈로 넘어가요
const OUCH_SECONDS = 1.8  // 끝난 뒤 "앗 뜨거!" 글자를 보여 주는 시간(초)
const ROAST_RATE = 0.2    // 불 쪽을 보는 면이 1초에 익는 정도 (양쪽을 5초씩 구우면 딱 1이 돼요)
const GOOD = [0.7, 1.3]   // 이 사이면 "노릇노릇". 넘으면 탄 것, 모자라면 덜 익은 것
const FLIP_SPEED = 12     // 뒤집는 빠르기 (1초에 도는 각도, 라디안)
const EYE_DIST = 1.3      // 카메라와 마시멜로 사이 거리(m)
const EYE_UP = 0.5        // 카메라가 마시멜로보다 이만큼(m) 위에 있어요

// 익은 정도에 따른 색: 흰색 → 노릇한 갈색 → 까맣게 탄 색
const RAW = new THREE.Color(0xfff6e6)
const GOLD = new THREE.Color(0xc98a3c)
const BURNT = new THREE.Color(0x2e1c10)
function roastColor(v, out) {
  if (v <= 1) return out.copy(RAW).lerp(GOLD, v)
  return out.copy(GOLD).lerp(BURNT, Math.min(1, (v - 1) / 0.6))
}

// events: onMini(state, text)  'start' 시작 / 'ouch' 굽기 끝(text: 결과 글자) / 'end' 끝
// item: missions.list의 항목 (pos: 문제 자리, prop: 소품 물체)
// 돌려주는 값: { update(dt) → 끝나면 true, cancel() }
export function createMarshmallowGame({ scene, camera, controls, player, item, onMini }) {
  // 그릴이 어디 있는지: 소품을 감싸는 상자로 가운데와 꼭대기(불꽃 끝)를 구해요
  const boxAll = new THREE.Box3().setFromObject(item.prop)
  const c = boxAll.getCenter(new THREE.Vector3())
  const tip = new THREE.Vector3(c.x, boxAll.max.y + 0.12, c.z) // 마시멜로 자리 (불꽃 바로 위)

  // 카메라 자리: 데크 가운데(문제 자리)에서 그릴 쪽으로 더 나간 바깥쪽. 텐트에 가리지 않게 하려는 거예요.
  // 그릴이 문제 자리와 겹쳐 있으면(데크가 아닐 때) 캐릭터가 서 있는 쪽을 써요
  const dir = new THREE.Vector3(c.x - item.pos.x, 0, c.z - item.pos.z)
  if (dir.length() < 0.5) dir.set(player.position.x - c.x, 0, player.position.z - c.z)
  if (dir.length() < 0.01) dir.set(0, 0, 1)
  dir.normalize()

  // 원래 카메라 자리를 적어 뒀다가 끝나면 되돌려요
  const savedPos = camera.position.clone()
  const savedTarget = controls.target.clone()
  const savedNear = camera.near
  controls.enabled = false
  player.visible = false // 1인칭이라 내 몸은 안 보여요
  // 평소에는 1m보다 가까운 것은 안 그려요. 손에 든 꼬치가 잘리지 않게 잠깐 가까운 것도 그리게 해요
  camera.near = 0.05
  camera.updateProjectionMatrix()

  camera.position.copy(tip).addScaledVector(dir, EYE_DIST)
  camera.position.y += EYE_UP
  camera.lookAt(tip)

  // ----- 꼬치와 마시멜로 -----
  const group = new THREE.Group()
  scene.add(group)
  // 꼬치: 화면 오른쪽 아래(손)에서 불 위까지
  const right = new THREE.Vector3().crossVectors(camera.getWorldDirection(new THREE.Vector3()), camera.up).normalize()
  const hand = camera.position.clone().addScaledVector(right, 0.28)
  hand.y -= 0.32
  const axis = tip.clone().sub(hand).normalize()
  const end = tip.clone().addScaledVector(axis, 0.14) // 꼬치 끝이 마시멜로 밖으로 조금 나와요
  const stickMat = new THREE.MeshStandardMaterial({ color: 0x9a9a9a, metalness: 0.6, roughness: 0.4 })
  const stick = new THREE.Mesh(new THREE.CylinderGeometry(0.008, 0.008, hand.distanceTo(end), 6), stickMat)
  stick.position.copy(hand).add(end).multiplyScalar(0.5)
  stick.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), axis)
  group.add(stick)

  // 마시멜로: 반쪽짜리 원기둥 두 개. 면마다 익은 정도(색)가 달라요
  const holder = new THREE.Group() // 꼬치 방향으로 눕힌 그룹
  holder.position.copy(tip)
  holder.quaternion.copy(stick.quaternion)
  const spin = new THREE.Group()   // 꼬치를 축으로 도는 그룹 (뒤집기)
  holder.add(spin)
  group.add(holder)
  const sides = [0, 1].map((i) => {
    const material = new THREE.MeshStandardMaterial({ color: RAW.clone(), roughness: 0.9 })
    const mesh = new THREE.Mesh(new THREE.CylinderGeometry(0.075, 0.075, 0.16, 16, 1, false, i * Math.PI, Math.PI), material)
    spin.add(mesh)
    return { material, mesh, roast: 0 }
  })

  // 처음에는 0번 면이 정확히 아래(불 쪽)를 보게 돌려 둬요
  // (0번 면의 가운데 방향은 spin의 +x 쪽이에요)
  holder.updateMatrixWorld(true)
  const q = holder.getWorldQuaternion(new THREE.Quaternion())
  const p = new THREE.Vector3(1, 0, 0).applyQuaternion(q).y
  const r = new THREE.Vector3(0, 0, 1).applyQuaternion(q).y
  let angle = Math.PI - Math.atan2(r, p) // 지금 각도
  let target = angle                     // 뒤집으면 여기에 반 바퀴씩 더해져요
  spin.rotation.y = angle

  // ----- 뒤집기: Space 또는 마우스 클릭 -----
  let time = 0
  let phase = 'play' // 'play' 굽는 중 → 'ouch' 앗 뜨거 → 'done'
  const flip = () => { if (phase === 'play') target += Math.PI }
  const onKey = (e) => {
    if (e.code !== 'Space' || e.repeat) return
    e.preventDefault() // 화면이 내려가거나 버튼이 눌리지 않게
    flip()
  }
  window.addEventListener('keydown', onKey)
  window.addEventListener('pointerdown', flip)

  onMini?.('start')

  const tmp = new THREE.Vector3()
  const col = new THREE.Color()
  function update(dt) {
    time += dt
    if (phase === 'play') {
      // 뒤집는 움직임
      angle += Math.min(target - angle, FLIP_SPEED * dt)
      spin.rotation.y = angle
      // 지금 아래(불 쪽)를 보는 면이 익어요
      const down = tmp.set(1, 0, 0).applyQuaternion(spin.getWorldQuaternion(q)).y < 0 ? 0 : 1
      sides[down].roast += ROAST_RATE * dt
      for (const s of sides) {
        s.material.color.copy(roastColor(s.roast, col))
        s.material.emissive.copy(col).multiplyScalar(0.25) // 밤에도 색이 보이게 살짝 빛나요
      }
      holder.position.y = tip.y + Math.sin(time * 3) * 0.008 // 손이 살짝 떨리는 느낌
      if (time >= PLAY_SECONDS) {
        phase = 'ouch'
        time = 0
        onMini?.('ouch', resultText())
      }
    } else if (phase === 'ouch') {
      // 깜짝 놀라 손을 빼는 느낌: 화면이 잠깐 흔들려요
      const shake = Math.max(0, 0.4 - time) * 0.06
      camera.position.copy(tip).addScaledVector(dir, EYE_DIST)
      camera.position.y += EYE_UP + Math.sin(time * 60) * shake
      camera.lookAt(tip)
      if (time >= OUCH_SECONDS) {
        finish()
        return true
      }
    }
    return false
  }

  function resultText() {
    const [a, b] = sides.map((s) => s.roast)
    if (Math.max(a, b) > GOOD[1]) return '한쪽이 까맣게 탔어요!'
    if (Math.min(a, b) < GOOD[0]) return '한쪽이 덜 익었어요!'
    return '노릇노릇 잘 구웠어요!'
  }

  // 꼬치·마시멜로를 치우고 카메라와 캐릭터를 원래대로 돌려놔요
  function finish() {
    if (phase === 'done') return
    phase = 'done'
    window.removeEventListener('keydown', onKey)
    window.removeEventListener('pointerdown', flip)
    scene.remove(group)
    stick.geometry.dispose()
    stickMat.dispose()
    for (const s of sides) { s.mesh.geometry.dispose(); s.material.dispose() }
    camera.position.copy(savedPos)
    controls.target.copy(savedTarget)
    camera.near = savedNear
    camera.updateProjectionMatrix()
    controls.enabled = true
    player.visible = true
    onMini?.('end')
  }

  return { update, cancel: finish }
}
