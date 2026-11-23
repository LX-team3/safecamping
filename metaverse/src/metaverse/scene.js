import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { loadTerrain } from './terrain.js'
import { loadGeo, loadGeoOptional, firstRing } from './geo.js'
import { disposeTree } from './shapes.js'
import { buildCampsite } from './campsite.js'
import { createPlayer } from './player.js'
import { createDayNight } from './daynight.js'
import { loadMissions, pickNextMission, findMissionPos, createMissions } from './missions.js'
import { createFlood } from './flood.js'

const DAY_NIGHT_SECONDS = 120 // 이 시간(초)마다 낮과 밤이 저절로 바뀌어요
const NEAR_DIST = 12          // 새 문제가 캐릭터와 이 거리(m)보다 가까우면 다른 문제를 골라요
// 돌발 미션 "비와 침수 대피"가 시작되는 때 (탐험한 시간 기준, 초). 이 사이에서 무작위로 정해요
const FIRST_RAIN = [40, 80]   // 처음 비가 오는 때
const NEXT_RAIN = [120, 200]  // 한 번 지나간 뒤 다음 비까지
const RAIN_SKY = { day: 0x8a9ba6, night: 0x0a0f1c } // 비 올 때 하늘색

// 주소 뒤에 ?rain=5 를 붙이면 시작하고 5초 뒤에 비가 와요 (발표·확인용). 없으면 무작위예요
const RAIN_PARAM = Number(new URLSearchParams(window.location.search).get('rain'))
// 주소 뒤에 ?quiz=c-n-throw 처럼 문제 id를 붙이면 그 문제만 떠요 (소품 확인·발표용). 없으면 무작위예요
const QUIZ_PARAM = new URLSearchParams(window.location.search).get('quiz')
const randBetween = ([a, b]) => a + Math.random() * (b - a)

// events: 화면(Vue)에 알려줄 일
//   onReady({ total })  준비가 끝났을 때
//   onMission(mission)  캐릭터가 미션 지점에 도착했을 때
//   onTime(mode)        낮·밤이 바뀌었을 때 ('day' 또는 'night')
//   onFlood(state)      침수 미션: 'start' 비 시작 / 'safe' 출구 도착 / 'drowned' 물에 잠김 / 'end' 끝
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
  let pool = []      // 불러온 문제 전체(40개). 여기서 하나씩 골라요
  let audience = 'child'   // 'child' 아이, 'adult' 어른 (시작 화면에서 골라요)
  const solved = new Set() // 이미 푼 문제 id. 같은 문제가 바로 또 나오지 않게 해요
  let cycleTime = 0        // 낮·밤이 바뀐 뒤 흐른 시간(초)
  let flood = null         // 침수 미션 (비, 차오르는 물, 출구 고리)
  let exploreTime = 0      // 탐험한 시간(초). 퀴즈를 푸는 동안과 침수 미션 중에는 멈춰요
  let rainAt = Infinity    // exploreTime이 이 값이 되면 비가 오기 시작해요
  let floodFrom = null     // 비가 오기 시작했을 때 캐릭터가 있던 자리 ("다시 대피하기" 때 돌아갈 곳)
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
    terrain = await loadTerrain('/data/terrain.asc', { flatArea: firstRing(boundary), carveAreas: bedRings })
    scene.add(terrain.mesh)

    camp = await buildCampsite(terrain)
    scene.add(camp.group)
    // 캠핑장을 불러오는 동안 밤을 골랐을 수도 있으니, 지금 낮·밤에 맞춰 가로등을 맞춰요
    camp.lamps.setNight(dayNight.mode === 'night')

    // 캐릭터 만들기 (카메라도 캐릭터 뒤로 옮겨요)
    player = createPlayer({ scene, camera, controls, terrain, camp })
    player.enabled = false // 시작 버튼을 누르기 전에는 못 움직여요
    dayNight.attachLamp(player.object) // 밤에 켜지는 손전등

    flood = createFlood({ scene, camp, terrain })

    pool = await loadMissions()
    events.onReady?.({ total: newRound() })
  }
  init().catch((err) => {
    console.error('불러오기 실패:', err)
    events.onError?.(err)
  })

  // 맵에 문제 하나를 무작위로 띄워요. 지금 고른 대상(아이/어른)과 지금 시간대(낮/밤)에 맞는 문제를 골라요.
  // avoid: 캐릭터 위치. 바로 발밑에 새 문제가 생기지 않게 가까운 곳은 피해요
  function spawnMission(avoid) {
    if (missions) missions.dispose()
    const forced = QUIZ_PARAM && pool.find((m) => m.id === QUIZ_PARAM) // 주소에 적은 문제가 있으면 그것만
    let pick = forced || null
    let pos = forced ? findMissionPos(forced, camp) : null
    for (let i = 0; i < 8 && !forced; i++) { // 가까운 곳이 걸리면 몇 번 더 뽑아 봐요
      pick = pickNextMission(pool, { audience, time: dayNight.mode, solved })
      pos = findMissionPos(pick, camp, avoid, NEAR_DIST) // 자리는 여기서 한 번만 골라요
      if (!avoid) break
      if (Math.hypot(pos.x - avoid.x, pos.z - avoid.z) > NEAR_DIST) break
    }
    // night: 밤이면 소품(모닥불, 랜턴)의 불빛을 켜요
    missions = createMissions({ scene, camp, terrain, missions: [pick], positions: [pos], night: dayNight.mode === 'night' })
  }

  // 새 판 준비: 푼 기록을 지우고 문제 하나를 띄운 뒤, 캐릭터를 시작 위치로 돌려놔요.
  // 지형·캠핑장은 그대로 두고 문제와 캐릭터만 바꿔서 빠르고 메모리도 아껴요
  // 돌려주는 값: 고른 대상(아이/어른)의 문제 수
  function newRound() {
    solved.clear()
    endFlood()
    spawnMission(null)
    player.reset(missions.list.map((m) => m.pos)) // 퀴즈 지점에서 떨어진 곳에서 시작
    exploreTime = 0
    rainAt = RAIN_PARAM > 0 ? RAIN_PARAM : randBetween(FIRST_RAIN)
    return pool.filter((m) => m.audience === audience).length
  }

  // ---------- 돌발 미션: 비와 침수 대피 ----------
  // 비 시작: 떠 있던 퀴즈를 치우고(도망가는 중에 퀴즈가 열리지 않게), 하늘을 흐리게 해요
  function startFlood() {
    if (!flood || flood.active) return
    if (missions) { missions.dispose(); missions = null }
    floodFrom = player.object.position.clone()
    flood.start()
    const sky = RAIN_SKY[dayNight.mode] ?? RAIN_SKY.day
    scene.background.set(sky)
    scene.fog.color.set(sky)
    events.onFlood?.('start')
  }
  // 비 끝: 물과 비를 치우고 하늘색을 원래대로 돌려요
  function endFlood() {
    if (!flood?.active) return
    flood.stop()
    dayNight.set(dayNight.mode)
    events.onFlood?.('end')
  }

  renderer.setAnimationLoop(() => {
    const now = performance.now()
    // 지난 프레임부터 흐른 시간(초). 다른 탭에 다녀오면 이 값이 아주 커져서
    // 낮·밤이 한꺼번에 넘어가거나 물이 순식간에 차 버리니까 0.5초까지만 인정해요
    const dt = Math.min((now - lastTime) / 1000, 0.5)
    lastTime = now
    // 탐험하는 동안 2분마다 낮·밤이 저절로 바뀌어요 (퀴즈를 푸는 동안과 침수 미션 중에는 시간이 멈춰요)
    if (playing && player?.enabled && !flood?.active) {
      cycleTime += dt
      if (cycleTime >= DAY_NIGHT_SECONDS) {
        cycleTime = 0
        setTime(dayNight.mode === 'day' ? 'night' : 'day')
      }
      // 정해 둔 때가 되면 비가 오기 시작해요
      exploreTime += dt
      if (exploreTime >= rainAt) startFlood()
    }
    // 침수 미션 진행: 출구에 닿으면 성공, 물에 잠기면 실패
    if (flood?.active && player) {
      const moving = playing && player.enabled
      const result = flood.update(dt, moving ? player.object.position : null, player.object.position)
      if (result) {
        player.enabled = false // 결과 창이 떠 있는 동안 멈춰요
        events.onFlood?.(result)
      }
    }
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
  // 문제를 맞혔을 때: 푼 문제로 적어 두고, 바로 다음 문제를 다른 곳에 띄워요
  function completeMission(id) {
    solved.add(id)
    if (camp && player) spawnMission(player.object.position)
    if (player && playing) player.enabled = true
  }
  // 대피 성공 뒤 "계속 탐험하기": 물을 치우고 퀴즈를 다시 띄워요. 다음 비는 한참 뒤에 와요
  function floodContinue() {
    endFlood()
    rainAt = exploreTime + randBetween(NEXT_RAIN)
    if (camp && player) spawnMission(player.object.position)
    if (player && playing) player.enabled = true
  }
  // 물에 잠긴 뒤 "다시 대피하기": 비가 오기 시작했던 자리로 돌아가서 처음부터 다시 해요
  function floodRetry() {
    if (!flood || !player) return
    flood.stop()
    if (floodFrom) player.moveTo(floodFrom.x, floodFrom.z)
    flood.start()
    events.onFlood?.('start')
    if (playing) player.enabled = true
  }

  // 아이·어른 고르기. 시작 전에 고르면 그 대상의 문제로 바꿔 띄워요. 문제 수를 돌려줘요
  function setAudience(who) {
    audience = who === 'adult' ? 'adult' : 'child'
    if (!camp || !player || playing) return 0
    return newRound()
  }
  function stop() {
    playing = false
    if (player) player.enabled = false
  }
  // "다시 하기": 3D 화면은 그대로 두고 새 문제로 새 판을 준비해요. 문제 수를 돌려줘요
  function restart() {
    stop()
    cycleTime = 0
    setTime('day') // 새 판은 낮부터 시작해요
    return newRound()
  }

  function dispose() {
    renderer.setAnimationLoop(null)
    if (missions) missions.dispose()
    if (flood) flood.dispose()
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
    events.onTime?.(dayNight.mode) // 화면 위쪽에 ☀️/🌙 표시를 바꾸라고 알려요
    // 낮에는 낮 문제만, 밤에는 밤 문제만 나오게: 떠 있던 문제를 새 시간대 문제로 바꿔요
    // (침수 미션 중에는 퀴즈를 띄우지 않아요)
    if (camp && player && pool.length && !flood?.active) spawnMission(playing ? player.object.position : null)
  }

  // 개발 서버(npm run dev)에서만: 브라우저 콘솔에서 __meta 로 캐릭터·문제·낮밤을 만져 볼 수 있어요 (소품 확인용)
  if (import.meta.env.DEV) {
    window.__meta = {
      camera, controls, setTime,
      get player() { return player },
      get missions() { return missions },
    }
  }

  return { start, completeMission, stop, restart, setAudience, floodContinue, floodRetry, dispose }
}
