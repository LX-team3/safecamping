// 앱 확인 팝업을 한 줄로 묻기 (브라우저 기본 창 window.confirm 대신)
//   if (!(await askConfirm({ title: '일정을 지울까요?', confirmLabel: '지우기' }))) return
// 팝업 그림은 App.vue 의 MobileConfirmHost 하나가 그린다. 휴대폰 뒤로가기는 취소로 본다
import { ref } from 'vue'
import { pushBack } from '@/utils/backStack'

export const asking = ref(null) // { title, message, confirmLabel, cancelLabel } 또는 null

let finish = () => {}

export function askConfirm(options) {
  answerConfirm(false) // 이미 떠 있으면 취소로 끝낸다
  return new Promise((resolve) => {
    const release = pushBack(() => answerConfirm(false))
    finish = (yes) => {
      finish = () => {}
      asking.value = null
      release()
      resolve(yes)
    }
    asking.value = options
  })
}

export function answerConfirm(yes) {
  finish(yes)
}
