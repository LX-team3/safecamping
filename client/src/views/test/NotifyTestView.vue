<script setup>
// 잠금화면 알림 테스트 전용 화면 (확인이 끝나면 이 파일과 라우터 한 줄을 지우면 됨)
import { ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import { LocalNotifications } from '@capacitor/local-notifications'
import { RAIN_DEMOS } from '@/data/rainWarningDemo'
import { scheduleRainDemoNotice } from '@/utils/localNotify'

const status = ref('버튼을 눌러 테스트하세요.')

// 안드로이드 8 이상은 "채널"로 알림 중요도를 정한다
// importance 5: 소리 + 화면 위 팝업 / visibility 1: 잠금화면에 내용까지 표시
async function ensureChannel() {
  await LocalNotifications.createChannel({
    id: 'test-alert',
    name: '테스트 알림',
    description: '잠금화면 알림 테스트용',
    importance: 5,
    visibility: 1,
  })
}

async function sendTest(delaySec) {
  if (!Capacitor.isNativePlatform()) {
    status.value = '앱에서만 동작해요. PC 브라우저에서는 알림이 뜨지 않아요.'
    return
  }

  const perm = await LocalNotifications.requestPermissions()
  if (perm.display !== 'granted') {
    status.value = '알림 권한이 거부됐어요. 폰 설정 › 애플리케이션 › 앱 › 알림을 켜 주세요.'
    return
  }

  await ensureChannel()
  await LocalNotifications.schedule({
    notifications: [{
      id: Math.floor(Math.random() * 100000),
      title: '캠핀 테스트 알림',
      body: '잠금화면에 이 알림이 보이면 성공이에요!',
      channelId: 'test-alert',
      schedule: {
        at: new Date(Date.now() + delaySec * 1000),
        allowWhileIdle: true,   // 폰이 잠겨 있어도 울리게
      },
    }],
  })

  status.value = delaySec > 1
    ? `${delaySec}초 뒤에 알림이 와요. 지금 바로 화면을 꺼서 잠가 보세요.`
    : '알림을 보냈어요. 위쪽 알림창을 확인해 보세요.'
}

// 인제군 호우특보 예시 알림 (시연용 더미). 5초 뒤 잠금화면 알림 → 누르면 재난지도가 그 비 양으로 열린다
const RAIN_MSG = {
  ok: '5초 뒤에 호우특보 예시 알림이 와요. 화면을 잠그고 기다린 뒤 알림을 눌러 보세요.',
  denied: '알림 권한이 거부됐어요. 폰 설정 › 애플리케이션 › 앱 › 알림을 켜 주세요.',
  web: '앱에서만 동작해요. PC 브라우저에서는 알림이 뜨지 않아요.',
  error: '알림을 예약하지 못했어요.',
}
async function sendRain(key) {
  status.value = RAIN_MSG[await scheduleRainDemoNotice(key, RAIN_DEMOS[key])]
}
</script>

<template>
  <main style="padding: 24px">
    <h1>알림 테스트</h1>
    <p>{{ status }}</p>
    <button type="button" style="display:block; width:100%; padding:16px; margin-top:16px" @click="sendTest(1)">
      지금 바로 알림
    </button>
    <button type="button" style="display:block; width:100%; padding:16px; margin-top:12px" @click="sendTest(10)">
      10초 뒤 알림 (잠금화면 테스트)
    </button>

    <h2 style="margin-top: 32px; font-size: 18px">인제군 호우특보 예시 (5초 뒤)</h2>
    <p style="font-size: 13px">실제 특보가 아닌 시연용 더미예요. 알림을 누르면 재난지도로 가요.</p>
    <button type="button" style="display:block; width:100%; padding:16px; margin-top:12px" @click="sendRain('advisory')">
      호우주의보 예시 (3시간 65mm · 준비)
    </button>
    <button type="button" style="display:block; width:100%; padding:16px; margin-top:12px" @click="sendRain('warning')">
      호우경보 예시 (3시간 95mm · 대피)
    </button>
  </main>
</template>
