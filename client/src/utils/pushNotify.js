// 안드로이드 앱(Capacitor)의 원격 푸시 알림 — Firebase Cloud Messaging(FCM)
//  - 로컬 알림(utils/localNotify.js)은 폰이 스스로 띄우는 것, 이 파일은 "밖(Firebase 콘솔 · 나중에 서버)에서 보낸 알림"을 받는 것
//  - 앱 시작 때 initPush(router) 를 한 번 부른다 (main.js). 권한을 확인하고 FCM 에 등록해 기기 토큰을 받는다
//  - 기기 토큰: 이 폰의 "받는 주소". 로그인하면 서버에 "이 회원의 폰"으로 등록하고(linkPushToken), 로그아웃하면 지운다(unlinkPushToken)
//    알림 테스트 화면(/app/test/notify)에도 보여 준다 → Firebase 콘솔 › Messaging › 테스트 메시지 전송에 붙여 넣어 보내 본다
//  - 알림을 누르면, 보낼 때 넣은 맞춤 데이터에 path 가 있으면 그 화면으로 간다 (나머지 값은 주소의 query 로)
//    예) path=/app/disaster, demo=warning → 호우경보 예시 재난지도
// PC · 모바일 브라우저에서는 아무 일도 하지 않는다 (앱 안에서만 동작)
// 앱에 client/android/app/google-services.json 이 있어야 한다 (없으면 등록이 실패하고 pushStatus 에 이유가 보임)
import { ref, watch } from 'vue'
import { Capacitor } from '@capacitor/core'
import { PushNotifications } from '@capacitor/push-notifications'
import { LocalNotifications } from '@capacitor/local-notifications'
import { api } from '@/utils/api'

// AndroidManifest.xml 의 기본 채널 이름과 같아야 한다 (콘솔에서 채널을 따로 안 정하고 보내면 이 채널로 온다)
const PUSH_CHANNEL_ID = 'push-alert'
let foregroundId = 700000 // 앱이 켜져 있을 때 다시 띄우는 로컬 알림 id (일정 id · 호우 예시 id 와 겹치지 않게)

export const pushToken = ref('') // 받은 기기 토큰 (알림 테스트 화면에서 보여 주고 복사)
export const pushStatus = ref('앱에서만 동작해요.') // 등록 진행 상황 · 실패 이유
export const linkStatus = ref('앱에서만 동작해요.') // 서버에 "이 회원의 폰"으로 등록됐는지

// 맞춤 데이터 { path, 나머지 } → 이동할 화면. FCM 이 덧붙이는 내부 값(google. · gcm. 으로 시작)은 뺀다
function targetOf(data) {
  const { path, ...rest } = data ?? {}
  if (!path) return null
  const query = Object.fromEntries(Object.entries(rest).filter(([k]) => !k.startsWith('google.') && !k.startsWith('gcm.')))
  return { path, query }
}

// 알림을 누르면 그 화면으로. t(누른 시각)를 붙여 이미 같은 화면에 있어도 다시 반응하게 한다 (localNotify 와 같은 방식)
function go(router, target) {
  if (!target) return
  console.info('[푸시] 눌림 →', target.path, target.query)
  router.push({ path: target.path, query: { ...target.query, t: String(Date.now()) } })
}

export async function initPush(router) {
  if (!Capacitor.isNativePlatform()) return

  // 등록 결과 · 받은 알림 · 누른 알림을 듣는다 (register() 전에 붙여야 첫 토큰을 놓치지 않는다)
  await PushNotifications.addListener('registration', ({ value }) => {
    pushToken.value = value
    pushStatus.value = '등록됨 — 아래 토큰을 Firebase 콘솔 테스트 메시지에 붙여 넣으세요.'
  })
  await PushNotifications.addListener('registrationError', (e) => {
    pushStatus.value = '등록 실패: ' + (e?.error ?? '알 수 없는 이유') + ' (google-services.json 확인)'
    console.warn('[푸시] 등록 실패', e)
  })
  // 앱이 켜져 있을 때 받은 푸시는 안드로이드가 알림창에 띄우지 않는다 → 로컬 알림으로 한 번 더 띄운다
  // 이 로컬 알림을 누르면 localNotify 의 initNotificationTap 이 extra.path 로 보내 준다
  await PushNotifications.addListener('pushNotificationReceived', (n) => {
    console.info('[푸시] 앱이 켜져 있을 때 받음', n)
    const target = targetOf(n.data)
    LocalNotifications.schedule({
      notifications: [{
        id: foregroundId++,
        title: n.title || '알림',
        body: n.body || '',
        channelId: PUSH_CHANNEL_ID,
        extra: target ?? {},
      }],
    }).catch((e) => console.warn('[푸시] 앱 안 알림 표시 실패', e))
  })
  // 앱이 꺼져 있거나 뒤에 있을 때 알림창에서 누른 경우
  await PushNotifications.addListener('pushNotificationActionPerformed', ({ notification }) => {
    go(router, targetOf(notification.data))
  })

  try {
    // 안드로이드 8 이상: 채널로 중요도를 정한다. importance 5 = 소리 + 화면 위 팝업, visibility 1 = 잠금화면에 내용까지
    await PushNotifications.createChannel({
      id: PUSH_CHANNEL_ID,
      name: '원격 알림',
      description: '서버 · Firebase 에서 보내는 캠핑 안전 알림',
      importance: 5,
      visibility: 1,
    })
    let perm = await PushNotifications.checkPermissions()
    if (perm.receive === 'prompt' || perm.receive === 'prompt-with-rationale') {
      perm = await PushNotifications.requestPermissions()
    }
    if (perm.receive !== 'granted') {
      pushStatus.value = '알림 권한이 없어요. 폰 설정 › 애플리케이션 › 앱 › 알림을 켜 주세요.'
      return
    }
    pushStatus.value = '등록 중…'
    await PushNotifications.register() // 결과는 위 'registration' · 'registrationError' 로 온다
  } catch (e) {
    pushStatus.value = '등록 실패: ' + e.message
    console.warn('[푸시] 초기화 실패', e)
  }
}

// 토큰 등록 요청. 로그인한 "직후"에는 서버가 CSRF 쿠키(XSRF-TOKEN)를 지워서, 바로 POST 를 보내면 403 으로 막힌다
// → GET 을 먼저 보내 새 쿠키를 받은 다음 보낸다
// 같은 때 다른 요청이 쿠키를 또 바꾸면 403 이 날 수 있어 그때만 한 번 더 해 본다
async function registerToken(token, retry = true) {
  await api('/api/auth/me')
  try {
    await api('/api/member/push-token', { method: 'post', body: { token } })
  } catch (e) {
    if (e.status !== 403 || !retry) throw e
    await registerToken(token, false)
  }
}

// 서버에 "이 회원의 폰은 여기"라고 알려 준다. 앱 시작 때 한 번 부르면 그 뒤로 알아서 보낸다 (main.js)
// 토큰과 로그인 중 무엇이 먼저 준비될지 몰라서 둘을 지켜보다가(watch) 둘 다 있을 때 보낸다
//  → 로그인했을 때, 로그인된 앱을 다시 켰을 때, 토큰이 바뀌었을 때 모두 이 한곳에서 처리된다
// getMemberId: 로그인한 회원 번호를 돌려주는 함수 (로그인 안 했으면 undefined). 서버에는 토큰만 보낸다 — 회원은 서버가 세션에서 안다
export function linkPushToken(getMemberId) {
  if (!Capacitor.isNativePlatform()) return
  watch([pushToken, getMemberId], async ([token, memberId]) => {
    if (!token || !memberId) {
      linkStatus.value = memberId ? '토큰을 기다리는 중…' : '로그인하면 이 폰이 등록돼요.'
      return
    }
    linkStatus.value = '서버에 등록 중…'
    try {
      await registerToken(token)
      // 응답을 기다리는 사이 로그아웃했으면 로그아웃 문구를 덮어쓰지 않는다
      if (getMemberId() === memberId) linkStatus.value = '서버에 등록됨 — 이 회원의 알림을 이 폰으로 받아요.'
    } catch (e) {
      if (getMemberId() === memberId) linkStatus.value = '서버 등록 실패: ' + e.message
      console.warn('[푸시] 서버 등록 실패', e.message)
    }
  }, { immediate: true })
}

// 로그아웃 "직전"에 부른다 (stores/auth.js). 로그아웃하고 나면 로그인이 필요한 API 라 지울 수 없다
// 실패해도 로그아웃은 되어야 하므로 에러를 밖으로 던지지 않는다
// 앱이 아니면 토큰이 비어 있어 아무 일도 하지 않는다
export async function unlinkPushToken() {
  if (!pushToken.value) return
  try {
    await api('/api/member/push-token', { method: 'delete', body: { token: pushToken.value } })
  } catch (e) {
    console.warn('[푸시] 서버 등록 해제 실패', e.message)
  }
}
