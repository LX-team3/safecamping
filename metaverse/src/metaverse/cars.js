// 달리는 자동차 (car_routes 레이어: 선으로 그린 차량 동선)
// 선의 시작점에서 나타나서 끝점까지 달리고 사라져요. 이걸 계속 반복해요
// 차 앞길에 캐릭터가 있으면 멈추고, 비키면 다시 달려요
// 필드: speed 속도(km/h), count 그 길을 달리는 차 대수
import { parts, pointAlong } from './geo.js'
import { makeCar } from './buildings.js'

const CAR = {
  length: 4.2, width: 1.8, // 차 크기 (m)
  speed: 10,               // speed 칸이 비어 있을 때 쓸 속도 (km/h)
  lift: 0.25,              // 도로 면 높이만큼 띄워요 (m)
  fade: 4,                 // 시작·끝에서 이 거리(m) 동안 커지고 작아져요 (갑자기 나타나지 않게)
  stopAhead: 8,          // 차 앞 이 거리(m) 안에 캐릭터가 있으면 멈춰요
  stopSide: 2.5,           // 동선에서 옆으로 이 거리(m) 안이면 "길 위에 있다"고 봐요
  brake: 4,                // 멈추고 출발하는 빠르기 (클수록 빨리 멈춰요)
}

// 선 전체 길이 (m)
function lineLength(pts) {
  let total = 0
  for (let i = 1; i < pts.length; i++) total += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1])
  return total
}

// 돌려주는 값: update(dt) 매 프레임 차를 앞으로 움직이기
// colliders: 캐릭터가 통과하지 못하는 곳 목록. 여기에 차의 충돌 상자를 넣어요
export function buildCars(routes, toWorld, heightAt, group, colliders = []) {
  const cars = [] // { obj, pts, total, speed, dist }
  const hw = CAR.width / 2
  const hl = CAR.length / 2

  for (const f of routes?.features || []) {
    const speed = ((Number(f.properties.speed) || CAR.speed) * 1000) / 3600 // km/h → m/s
    const count = Math.max(1, Number(f.properties.count) || 1)
    for (const line of parts(f.geometry)) {
      const pts = line.map(toWorld)
      const total = lineLength(pts)
      if (pts.length < 2 || total < 1) continue // 너무 짧은 선은 건너뛰어요
      for (let i = 0; i < count; i++) {
        // 차 모양은 주차장 차와 같아요. 네모(가로 × 길이)를 넘기면 그 크기로 만들어 줘요
        // (색은 네모 위치로 정해져서, i를 더해 차마다 색이 달라지게 했어요. 크기는 그대로예요)
        const obj = makeCar([[-hw + i, -hl], [hw + i, -hl], [hw + i, hl], [-hw + i, hl]])
        group.add(obj)
        // 여러 대면 같은 간격으로 떨어뜨려서 출발해요
        // speed: 평소 속도, now: 지금 속도 (캐릭터가 앞에 있으면 0까지 줄어요)
        // wall: 캐릭터가 차를 통과하지 못하게 막는 네모 상자. 차가 움직일 때마다 같이 옮겨요
        const wall = { x: 0, z: 0, hw, hd: hl, angle: 0 }
        colliders.push(wall)
        cars.push({ obj, wall, pts, total, speed, now: speed, dist: (total * i) / count })
      }
    }
  }

  function place(c) {
    const p = pointAlong(c.pts, c.dist / c.total)
    c.obj.position.set(p.x, heightAt(p.x, p.z) + CAR.lift, p.z)
    c.obj.rotation.y = p.angle // 가는 방향을 바라봐요
    // 시작점 근처에서는 점점 커지고, 끝점 근처에서는 점점 작아져요
    const s = Math.max(0.01, Math.min(1, c.dist / CAR.fade, (c.total - c.dist) / CAR.fade))
    c.obj.scale.setScalar(s)
    // 충돌 상자도 차와 같은 자리·방향·크기로 맞춰요
    c.wall.x = p.x
    c.wall.z = p.z
    c.wall.angle = p.angle
    c.wall.hw = hw * s
    c.wall.hd = hl * s
  }
  cars.forEach(place)

  // 차 앞길(동선 위 stopAhead m까지)에 캐릭터가 있는지
  // 길이 굽어 있어도 맞게, 동선을 따라 2m마다 한 점씩 찍어서 캐릭터와의 거리를 재요
  function blocked(c, who) {
    if (!who) return false
    for (let d = 0; d <= CAR.stopAhead; d += 2) {
      const ahead = c.dist + d
      if (ahead > c.total) break // 동선 끝을 넘어가면 더 볼 필요 없어요
      const p = pointAlong(c.pts, ahead / c.total)
      if (Math.hypot(who.x - p.x, who.z - p.z) < CAR.stopSide) return true
    }
    return false
  }

  return {
    count: cars.length,
    // who: 캐릭터 위치 { x, z }. 차 앞길에 있으면 차가 멈추고, 비키면 다시 달려요
    update(dt, who) {
      for (const c of cars) {
        const goal = blocked(c, who) ? 0 : c.speed
        c.now += (goal - c.now) * Math.min(1, dt * CAR.brake) // 속도를 부드럽게 줄이고 올려요
        c.dist = (c.dist + c.now * dt) % c.total // 끝에 닿으면 다시 시작점으로
        place(c)
      }
    },
  }
}
