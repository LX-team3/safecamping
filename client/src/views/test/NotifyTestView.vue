<script setup>
// 잠금화면 알림 테스트 전용 화면 (확인이 끝나면 이 파일과 라우터 한 줄을 지우면 됨)
import { ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import { LocalNotifications } from '@capacitor/local-notifications'

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
  </main>
</template>
