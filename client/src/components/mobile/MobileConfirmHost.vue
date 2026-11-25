<script setup>
// utils/confirm.js 의 askConfirm() 이 띄우는 팝업 (App.vue 에 한 번만 둔다)
import { watch } from 'vue'
import { useRoute } from 'vue-router'
import MobileConfirm from '@/components/mobile/MobileConfirm.vue'
import { answerConfirm, asking } from '@/utils/confirm'

// 알림을 눌러 다른 화면으로 넘어가면 팝업도 취소로 닫는다
const route = useRoute()
watch(() => route.path, () => answerConfirm(false))
</script>

<template>
  <MobileConfirm v-if="asking" open v-bind="asking" @confirm="answerConfirm(true)" @cancel="answerConfirm(false)" />
</template>
