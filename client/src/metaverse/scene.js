import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { loadTerrain } from './terrain.js'
import { loadGeo, loadGeoOptional, firstRing } from './geo.js'
import { disposeTree } from './shapes.js'
import { buildCampsite } from './campsite.js'
import { createPlayer } from './player.js'
import { createDayNight } from './daynight.js'
import { loadMissions, pickMissions, createMissions } from './missions.js'

// events: 화면(Vue)에 알려줄 일
//   onReady({ total })  준비가 끝났을 때
//   onMission(mission)  캐릭터가 미션 지점에 도착했을 때
//   onError(err)        불러오다가 실패했을 때
export function createScene(container, events = {}) {
  const scene = new THREE.Scene()
  scene.background = new THREE.Color(0xbfe3f5)
  scene.fog = new THREE.Fog(0xbfe3f5, 1500, 4000)

  const camera = new THREE.PerspectiveCamera(
    55, container.clientWidth / container.clientHeight, 1, 10000
  )
  camera.position.set(900, 700, 900)

  // logarithmicDepthBuffer: 바닥과 도로가 겹쳐 깜빡이는 것을 막아요
  const renderer = new THREE.WebGLRenderer({ antialias: true, logarithmicDepthBuffer: true })
  renderer.setSize(container.clientWidth, container.clientHeight)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  container.appendChild(renderer.domElement)

  const hemi = new THREE.HemisphereLight(0xffffff, 0x88aa66, 1.2)
  scene.add(hemi)
  const sun = new THREE.DirectionalLight(0xffffff, 2)
  sun.position.set(500, 1000, 300)
  scene.add(sun)
  // 낮·밤 바꾸기 (하늘색, 빛, 별과 달, 손전등)
  const dayNight = createDayNight({ scene, hemi, sun })

  const controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.maxPolarAngle = Math.PI / 2.1

  let player = null
  let camp = null
  let missions = null
  let terrain = null
  let pool = []      // 불러온 문제 전체. "다시 하기" 때 여기서 새로 골라요
  let playing = false
  let lastTime = performance.now()

  // 순서: 캠핑장 테두리 읽기 → 테두리 안을 평평하게 한 지형 → 캠핑장 시설
  async function init() {
    const boundary = await loadGeo('boundary')
    // 강바닥(bed)을 지형에서 낮게 파낼 수 있게 미리 읽어요 (파일이 없으면 그냥 넘어가요)
    const river = await loadGeoOptional('river')
    const bedRings = []
    for (const f of river?.features || []) {
      if (f.properties.type !== 'bed') continue
      const g = f.geometry
      if (g.type === 'Polygon') bedRings.push(g.coordinates[0])
      if (g.type === 'MultiPolygon') for (const p of g.coordinates) bedRings.push(p[0])
    }
    terrain = await loadTerrain(`${import.meta.env.BASE_URL}metaverse/data/terrain.asc`, { flatArea: firstRing(boundary), carveAreas: bedRings })
    scene.add(terrain.mesh)

    camp = await buildCampsite(terrain)
    scene.add(camp.group)
    // 캠핑장을 불러오는 동안 밤을 골랐을 수도 있으니, 지금 낮·밤에 맞춰 가로등을 맞춰요
    camp.lamps.setNight(dayNight.mode === 'night')

    // 캐릭터 만들기 (카메라도 캐릭터 뒤로 옮겨요)
    player = createPlayer({ scene, camera, controls, terrain, camp, domElement: renderer.domElement })
    player.enabled = false // 시작 버튼을 누르기 전에는 못 움직여요
    dayNight.attachLamp(player.object) // 밤에 켜지는 손전등

    pool = await loadMissions()
    events.onReady?.({ total: newRound() })
  }
  init().catch((err) => {
    console.error('불러오기 실패:', err)
    events.onError?.(err)
  })

  // 새 판 준비: 문제 4개를 새로 고르고, 캐릭터를 시작 위치로 돌려놔요.
  // 지형·캠핑장은 그대로 두고 문제와 캐릭터만 바꿔서 빠르고 메모리도 아껴요
  // 돌려주는 값: 이번 판 문제 수
  function newRound() {
    if (missions) missions.dispose()
    missions = createMissions({ scene, camp, terrain, missions: pickMissions(pool, 4) })
    player.reset(missions.list.map((m) => m.pos)) // 퀴즈 지점에서 떨어진 곳에서 시작
    return missions.list.length
  }

  renderer.setAnimationLoop(() => {
    const now = performance.now()
    const dt = (now - lastTime) / 1000 // 지난 프레임부터 흐른 시간(초)
    lastTime = now
    if (camp) camp.update(dt, player?.object.position)
    if (player) player.update(dt)
    if (missions) {
      const arrived = missions.update(dt, playing && player ? player.object.position : null)
      if (arrived) {
        player.enabled = false // 퀴즈를 푸는 동안 멈춰요
        events.onMission?.(arrived)
      }
    }
    controls.update()
    renderer.render(scene, camera)
  })

  const onResize = () => {
    camera.aspect = container.clientWidth / container.clientHeight
    camera.updateProjectionMatrix()
    renderer.setSize(container.clientWidth, container.clientHeight)
  }
  window.addEventListener('resize', onResize)

  // 화면(Vue)에서 부르는 함수들
  function start() {
    playing = true
    if (player) player.enabled = true
  }
  function completeMission(id) {
    missions?.complete(id)
    if (player && playing) player.enabled = true
  }
  function stop() {
    playing = false
    if (player) player.enabled = false
  }
  // "다시 하기": 3D 화면은 그대로 두고 새 문제로 새 판을 준비해요. 문제 수를 돌려줘요
  function restart() {
    stop()
    return newRound()
  }

  function dispose() {
    renderer.setAnimationLoop(null)
    if (missions) missions.dispose()
    if (player) player.dispose()
    window.removeEventListener('resize', onResize)
    controls.dispose()
    disposeTree(scene) // 지형·캠핑장·캐릭터가 쓰던 그래픽 메모리 돌려주기
    renderer.dispose()
    container.removeChild(renderer.domElement)
  }

  // 낮·밤 바꾸기. mode: 'day' 또는 'night'
  function setTime(mode) {
    dayNight.set(mode)
    camp?.lamps.setNight(dayNight.mode === 'night') // 가로등 켜고 끄기
  }

  return { start, completeMission, stop, restart, setTime, dispose }
}
