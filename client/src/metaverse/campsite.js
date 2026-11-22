// 캠핑장 전체 만들기: QGIS에서 내보낸 파일을 읽어서 순서대로 배치해요
// 세부 모양은 파일을 나눠 뒀어요
//   ground.js 바닥·도로·바닥분수 / river.js 강 / buildings.js 건물
//   sites.js 데크·카라반·주차칸 / trees.js 나무 / shapes.js 모양 도우미 / geo.js 도형 계산
import * as THREE from 'three'
import {
  loadGeo, loadGeoOptional, parts, polygonsWithHoles, firstRing, openRing,
  centroid, pointInRing, distToSegs, inShape, pointAlong, hitsCollider,
} from './geo.js'
import { flatMat, flatPolygon, densify, ribbon } from './shapes.js'
import { makeArea, makeSplash } from './ground.js'
import { buildRiver } from './river.js'
import { BUILDING_STYLE, makeBuilding } from './buildings.js'
import {
  ROW_SHRINK, FACILITY, makeDeck, makeCaravan, makeParking, makeSite,
  parseSiteName, snapToRoad, straightenRow, addColliders,
} from './sites.js'
import { FOREST, SMALL_TREE, plantForest } from './trees.js'
import { buildCars } from './cars.js'
import { buildLamps } from './lamps.js'

export async function buildCampsite(terrain) {
  const { cx, cy, heightAt } = terrain
  const toWorld = (p) => [p[0] - cx, -(p[1] - cy)] // EPSG:5179 → 3D (x, z)
  const group = new THREE.Group()
  const colliders = [] // 캐릭터가 통과하지 못하는 곳

  // boundary(캠핑장 테두리)만 꼭 있어야 해요.
  // 나머지는 파일이 없어도 그 부분만 빼고 만들어요 (게임이 멈추지 않게)
  const [boundary, roads, stream, rows, facilities, riverGeo, areas, sites, buildings, carRoutes] = await Promise.all([
    loadGeo('boundary'),
    ...['roads', 'stream', 'site_rows', 'facilities', 'river', 'areas', 'sites', 'buildings', 'car_routes'].map(loadGeoOptional),
  ])

  // 1) 캠핑장 바닥 (잔디)
  const ring = firstRing(boundary).map(toWorld)
  group.add(flatPolygon(ring, flatMat(0x9cc46a), terrain.floor + 0.1))

  // 1-1) 강
  const river = buildRiver(riverGeo, toWorld, terrain.bedLevel ?? terrain.floor - 3, group)

  // 1-2) 바닥 영역: 보도블록, 주차장, 분수대 광장 (잔디보다 위, 도로보다 아래)
  const builtTypes = new Set() // 모양을 딴 시설 type. 같은 type의 시설 점은 건너뛰어요
  const animated = []          // 매 프레임 움직이는 것 (분수 물줄기)
  const forestZones = []       // 나무 심을 영역
  const smallTreeZones = []    // 작은 나무 심을 영역
  const roadPolys = []         // 도로 모양 { outer, holes }
  for (const f of areas?.features || []) {
    for (const poly of polygonsWithHoles(f.geometry)) {
      const r = poly[0]
      const holesW = poly.slice(1).map((h) => openRing(h.map(toWorld)))
      if (f.properties.type === 'road') {
        const rw = openRing(r.map(toWorld))
        roadPolys.push({ outer: rw, holes: holesW })
        group.add(makeArea(rw, 'road', terrain.floor + 0.25, holesW)) // 다른 바닥보다 위에
      } else if (f.properties.type === 'small_tree') {
        smallTreeZones.push(r.map(toWorld)) // 작은 나무도 맨 마지막에 심어요 (건물·도로를 피하려고)
      } else if (f.properties.type === 'forest') {
        forestZones.push(r.map(toWorld)) // 나무는 맨 마지막에 심어요 (도로·개울을 피하려고)
      } else if (f.properties.type === 'splash') {
        const sp = makeSplash(openRing(r.map(toWorld)), terrain.floor + 0.2)
        group.add(sp)
        animated.push(sp)
        builtTypes.add('fountain') // 예전 분수대 점은 건너뛰어요
      } else {
        group.add(makeArea(r.map(toWorld), f.properties.type, terrain.floor + 0.17, holesW))
      }
    }
  }

  // 2) 도로 (roads 레이어, 폴리곤): 그린 모양 그대로 아스팔트를 깔아요
  const roadLines = [] // 퀴즈가 "road": "3" 처럼 찾을 수 있게 이름과 테두리를 남겨요
  for (const f of roads?.features || []) {
    const polys = polygonsWithHoles(f.geometry)
    if (!polys.length) console.warn('roads 레이어는 폴리곤으로 그려 주세요. 건너뛴 도형:', f.geometry?.type)
    for (const poly of polys) {
      const rw = openRing(poly[0].map(toWorld))
      const holesW = poly.slice(1).map((h) => openRing(h.map(toWorld)))
      roadPolys.push({ outer: rw, holes: holesW })
      group.add(makeArea(rw, 'road', terrain.floor + 0.25, holesW))
      roadLines.push({ name: String(f.properties.name ?? ''), pts: rw })
    }
  }

  // 3) 개울
  const streamMat = flatMat(0x5bb0e0)
  const streamLines = [] // 개울 미션 위치를 정할 때 써요
  for (const f of stream?.features || []) {
    for (const line of parts(f.geometry)) {
      streamLines.push(line.map(toWorld))
      const pts = densify(line.map(toWorld), 5)
      group.add(ribbon(pts, 3, (x, z) => heightAt(x, z) + 0.3, streamMat))
    }
  }

  // 4-1) 사이트 폴리곤 (sites 레이어가 있으면)
  const drawnRows = new Set() // 폴리곤으로 그린 줄 이름. site_rows의 같은 줄은 건너뛰어요
  let siteIdx = 0
  for (const f of sites?.features || []) {
    const type = f.properties.type || 'deck'
    const id = parseSiteName(f.properties.name)
    for (const r of parts(f.geometry)) {
      const ringW = openRing(r.map(toWorld))
      const obj = makeSite(ringW, type, siteIdx++, heightAt, f.properties.name)
      obj.userData = { ...obj.userData, row: id?.row, no: id?.no, siteType: type }
      group.add(obj)
      if (id) drawnRows.add(id.row)
      // 부딪힘: 물건이 돌아간 방향 그대로
      const probe = { position: obj.position, rotation: { y: type === 'caravan' ? obj.userData.angle + Math.PI / 2 : obj.userData.angle } }
      if (type === 'caravan') addColliders(colliders, probe, 'caravan')
      else if (type !== 'parking') addColliders(colliders, probe, 'deck')
    }
  }

  // 4-2) 데크·카라반·주차 줄 (선으로 그린 것. 폴리곤으로 그린 줄은 건너뛰어요)
  for (const f of rows?.features || []) {
    const name = String(f.properties.name ?? '')
    if (drawnRows.has(name.toUpperCase())) continue
    const count = Number(f.properties.count) || 1
    for (const line of parts(f.geometry)) {
      const pts = line.map(toWorld)
      const rowObjs = [] // 이 줄에 놓인 것들 (방향 바로잡기용)
      for (let i = 0; i < count; i++) {
        // 첫 데크 ~ 마지막 데크. 주차장(P)을 뺀 줄은 가운데 쪽으로 모아서 간격을 줄여요
        const shrink = name === 'P' ? 1 : ROW_SHRINK
        const t = count === 1 ? 0.5 : 0.5 + (i / (count - 1) - 0.5) * shrink
        const p = pointAlong(pts, t)
        let obj
        if (name === 'F') {
          obj = makeCaravan(`${name}-${i + 1}`) // 번호판 글자 (예: F-12)
          // 카라반: 도로와 직각, 짧은 끝이 도로에 붙어요 (길이 7m의 절반)
          snapToRoad(obj, p.x, p.z, 3.6, heightAt, roadPolys, p.angle + Math.PI / 2)
        } else if (name === 'P') {
          obj = makeParking()
          obj.rotation.y = p.angle + Math.PI / 2 // 주차칸은 줄과 직각
          obj.position.set(p.x, heightAt(p.x, p.z), p.z)
        } else {
          obj = makeDeck(i)
          // 데크: 도로와 나란하게, 한 변이 도로에 붙어요 (7m의 절반)
          snapToRoad(obj, p.x, p.z, 3.5, heightAt, roadPolys, p.angle)
        }
        obj.userData = { row: name, no: i + 1 } // 예: B줄 3번 데크
        group.add(obj)
        if (name !== 'P') rowObjs.push(obj)
      }
      // 혼자 삐뚤어진 것 바로잡고, 그다음 충돌 상자를 붙여요
      if (rowObjs.length > 1) straightenRow(rowObjs, name === 'F' ? Math.PI : Math.PI / 2)
      for (const obj of rowObjs) addColliders(colliders, obj, name === 'F' ? 'caravan' : 'deck')
    }
  }

  // 캠핑장 가운데 (건물 정면 방향, 퀴즈 위치 보정, 시작 위치에 써요)
  const center = centroid(ring)

  // 5) 건물 (buildings 레이어: 바닥 모양대로 세워요)
  for (const f of buildings?.features || []) {
    const type = f.properties.type
    for (const r of parts(f.geometry)) {
      const ringW = openRing(r.map(toWorld))
      group.add(makeBuilding(ringW, type, heightAt, center))
      builtTypes.add(type)
      // 기둥만 있는 개수대는 가운데 싱크대만 막고, 나머지는 건물 모양 전체를 막아요
      if (BUILDING_STYLE[type]?.open) {
        const [x, z] = centroid(ringW)
        colliders.push({ x, z, hw: 1.5, hd: 0.5, angle: 0 })
      } else {
        colliders.push({ poly: ringW })
      }
    }
  }

  // 6) 시설 (facilities 레이어: 점으로 찍은 것)
  let start = null // 캐릭터 시작 위치. QGIS에서 type을 start로 적은 점이 있으면 거기서 시작해요
  const lampSpots = [] // 가로등 위치 (type이 lamp인 점)
  for (const f of facilities?.features || []) {
    const type = f.properties.type
    if (type === 'start') {
      const [p] = parts(f.geometry)[0] || []
      if (p) start = toWorld(p)
      continue
    }
    if (type === 'lamp') { // 가로등: 위치만 모아 두고 아래에서 한꺼번에 세워요
      for (const [p] of parts(f.geometry)) lampSpots.push(toWorld(p))
      continue
    }
    // 건물 종류는 buildings 레이어(폴리곤)로 세우니까 점은 건너뛰어요
    if (builtTypes.has(type) || BUILDING_STYLE[type]) continue
    const make = FACILITY[type]
    if (!make) {
      console.warn('모양이 정해지지 않은 facilities type:', type)
      continue
    }
    for (const [p] of parts(f.geometry)) {
      const [x, z] = toWorld(p)
      const obj = make()
      obj.position.set(x, heightAt(x, z), z)
      if (type === 'deck') snapToRoad(obj, x, z, 3.5, heightAt, roadPolys) // 데크는 도로와 나란하게
      obj.userData = { type, name: f.properties.name }
      addColliders(colliders, obj, type)
      group.add(obj)
    }
  }

  // 6-1) 가로등 (밤 버전에서 불이 켜져요)
  const lamps = buildLamps(lampSpots, heightAt, group, colliders)

  // 도로 위이거나 도로 가장자리에서 m(m)보다 가까운지 (나무를 심지 않을 곳)
  const nearRoad = (x, z, m) =>
    roadPolys.some((r) => inShape(x, z, r) || [r.outer, ...r.holes].some((g) => distToSegs(x, z, g, true) < m))

  // 7) 숲: forest 영역 안에 나무 심기
  //    캠핑장 안(+여유), 강바닥, 도로, 개울 위에는 심지 않아요
  if (forestZones.length) {
    const bedRings = []
    for (const f of riverGeo?.features || []) {
      if (f.properties.type === 'bed') for (const r of parts(f.geometry)) bedRings.push(r.map(toWorld))
    }
    const avoid = (x, z) =>
      pointInRing(x, z, ring) || distToSegs(x, z, ring, true) < FOREST.margin ||
      bedRings.some((r) => pointInRing(x, z, r)) ||
      nearRoad(x, z, 2) ||
      streamLines.some((l) => distToSegs(x, z, l, false) < 3)
    const forest = plantForest(forestZones, heightAt, avoid)
    for (const m of forest.meshes) group.add(m)
    console.log(`나무 ${forest.count}그루를 심었어요`)
  }

  // 8) 작은 나무: small_tree 영역 안에 심기
  //    캠핑장 안에도 심어요. 도로·건물·텐트·카라반·개울 위에는 심지 않아요
  if (smallTreeZones.length) {
    const avoid = (x, z) =>
      nearRoad(x, z, 1) ||
      hitsCollider(x, z, colliders, 1.5) ||
      streamLines.some((l) => distToSegs(x, z, l, false) < 2)
    const small = plantForest(smallTreeZones, heightAt, avoid, SMALL_TREE)
    for (const m of small.meshes) group.add(m)
    // 작은 나무 줄기마다 작은 충돌 상자를 붙여서 통과하지 못하게 해요
    if (SMALL_TREE.block > 0) {
      for (const t of small.spots) colliders.push({ x: t.x, z: t.z, hw: SMALL_TREE.block, hd: SMALL_TREE.block, angle: 0 })
    }
    console.log(`작은 나무 ${small.count}그루를 심었어요`)
  }

  // 9) 달리는 자동차 (car_routes 레이어의 선을 따라가요)
  //    작은 나무를 다 심은 뒤에 만들어요 (차의 충돌 상자 때문에 나무가 빠지지 않게)
  const cars = buildCars(carRoutes, toWorld, heightAt, group, colliders)

  // 매 프레임 움직일 것들 (물결, 분수, 자동차)
  // who: 캐릭터 위치 (차가 캐릭터를 보고 멈추는 데 써요)
  function update(dt, who) {
    river.update(dt)
    cars.update(dt, who)
    lamps.update(who)
    for (const a of animated) a.userData.update(dt)
  }

  return { group, center, start, ring, streamLines, roadLines, colliders, river, lamps, update }
}
