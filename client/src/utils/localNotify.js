// 안드로이드 앱(Capacitor)의 로컬 알림 — 서버 · 푸시 없이 폰이 직접 예약해서 띄운다
//  - scheduleBriefNotice: 캠핑 일정을 저장하면 5초 뒤 "캠핑 브리핑" 알림 (시연용. 실제로는 캠핑 전날 저녁 등으로 바꿀 자리)
//  - scheduleRainDemoNotice: 인제군 호우특보 예시 알림 (시연용 더미, data/rainWarningDemo.js) → 누르면 재난지도
//  - initNotificationTap: 알림을 누르면 알림에 적어 둔 화면(extra.path · extra.query)으로 간다
// PC · 모바일 브라우저에서는 아무 일도 하지 않는다 (앱 안에서만 동작)
import { Capacitor } from '@capacitor/core'
import { LocalNotifications } from '@capacitor/local-notifications'

const CHANNEL_ID = 'camp-brief'
const DISASTER_CHANNEL_ID = 'disaster-alert'
const DELAY_MS = 5 * 1000 // 시연용 5초
const RAIN_DEMO_IDS = { advisory: 900001, warning: 900002 } // 일정 id 와 겹치지 않게 큰 수

const isApp = () => Capacitor.isNativePlatform()

// 안드로이드 8 이상은 채널로 중요도를 정한다. importance 5: 소리 + 화면 위 팝업, visibility 1: 잠금화면에 내용까지 표시
async function ensureChannel() {
  await LocalNotifications.createChannel({
    id: CHANNEL_ID,
    name: '캠핑 브리핑',
    description: '캠핑 전 특보 · 날씨 · 준비물 브리핑',
    importance: 5,
    visibility: 1,
  })
}

// 재난 알림 채널: 브리핑과 따로 둬서, 사용자가 폰 설정에서 브리핑만 끄더라도 재난 알림은 남게 한다
async function ensureDisasterChannel() {
  await LocalNotifications.createChannel({
    id: DISASTER_CHANNEL_ID,
    name: '재난 · 기상특보',
    description: '캠핑장 지역 호우특보 등 대피가 필요할 수 있는 알림',
    importance: 5,
    visibility: 1,
  })
}

// 알림 권한을 묻고, 허용됐는지 돌려준다
async function granted() {
  const perm = await LocalNotifications.requestPermissions()
  return perm.display === 'granted'
}

// plan: { id, campName } — 방금 저장한 일정. 실패해도 일정 저장에는 영향을 주지 않도록 에러를 밖으로 던지지 않는다
export async function scheduleBriefNotice(plan) {
  if (!isApp() || !plan) return
  try {
    if (!(await granted())) return // 알림을 거부했으면 조용히 넘어간다
    await ensureChannel()
    await LocalNotifications.schedule({
      notifications: [{
        id: plan.id, // 일정 id 를 그대로 쓴다 → 같은 일정을 다시 저장하면 앞 알림을 덮어써서 겹치지 않는다
        title: '캠핑 브리핑이 도착했어요',
        body: `${plan.campName} · 특보 · 날씨 · 준비물을 확인해 보세요`,
        channelId: CHANNEL_ID,
        schedule: { at: new Date(Date.now() + DELAY_MS), allowWhileIdle: true }, // 잠금 · 절전 중에도 울리게
        extra: { path: '/app/my-camping', query: { brief: String(plan.id) } }, // 눌렀을 때 갈 곳
      }],
    })
  } catch (e) {
    console.warn('브리핑 알림 예약 실패', e)
  }
}

// demo: data/rainWarningDemo.js 의 RAIN_DEMOS 중 하나, key: 'advisory' · 'warning'
// 잠금화면에는 제목 + 첫 줄, 알림을 아래로 펼치면 3줄 전부(largeBody, 안드로이드 큰 글 모양)가 보인다
// 돌려주는 값: 'ok' · 'denied'(권한 거부) · 'web'(앱이 아님) · 'error'
export async function scheduleRainDemoNotice(key, demo) {
  if (!isApp()) return 'web'
  try {
    if (!(await granted())) return 'denied'
    await ensureDisasterChannel()
    await LocalNotifications.schedule({
      notifications: [{
        id: RAIN_DEMO_IDS[key],
        title: demo.title,
        body: demo.lines[0],
        largeBody: demo.lines.join('\n'),
        channelId: DISASTER_CHANNEL_ID,
        schedule: { at: new Date(Date.now() + DELAY_MS), allowWhileIdle: true },
        extra: { path: '/app/disaster', query: { demo: key } },
      }],
    })
    return 'ok'
  } catch (e) {
    console.warn('호우특보 예시 알림 예약 실패', e)
    return 'error'
  }
}

// 앱 시작 때 한 번 부른다 (main.js). 앱이 꺼져 있다가 알림으로 켜진 경우에도 이 이벤트가 온다
export function initNotificationTap(router) {
  if (!isApp()) return
  LocalNotifications.addListener('localNotificationActionPerformed', ({ notification }) => {
    const { path, query } = notification.extra ?? {}
    if (!path) return
    // t: 누른 시각 — 이미 같은 주소에 있어도 주소가 바뀌어서 화면이 다시 반응한다 (브리핑 펼치기 · 예시 비 다시 보여 주기)
    router.push({ path, query: { ...(query ?? {}), t: String(Date.now()) } })
  })
}
