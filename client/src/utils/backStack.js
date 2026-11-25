// 휴대폰·브라우저 "뒤로가기" 도우미
//  pushBack(onBack): 시트를 열거나 다음 단계로 갈 때 부른다. 같은 주소로 기록을 한 칸 쌓아 두고,
//    사용자가 뒤로가기를 누르면 화면을 나가는 대신 onBack 을 부른다 (시트 닫기 · 이전 단계)
//    돌려받은 release() 는 X·적용 버튼처럼 뒤로가기가 아닌 방법으로 닫을 때 부른다 (쌓은 칸을 치운다)
//  goUp(router, target): 끝난 화면(로그인·진단 등)에서 target 으로 나간다. 바로 앞이 target 이면 뒤로, 아니면 이 화면을 바꿔치기
//    → 뒤로가기를 눌러도 끝난 화면이 다시 나오지 않는다
// router/index.js 가 initBackStack(router) 을 한 번 부른다
let router = null
const stack = [] // 쌓인 칸들 [{ onBack, pushed }], 마지막이 맨 위
let pending = Promise.resolve() // 칸을 치우는 중이면 끝날 때까지 (history.go 는 바로 끝나지 않는다)
let ignorePop = false // 우리가 직접 치운 뒤로가기는 onBack 을 부르지 않는다

export function initBackStack(r) {
  router = r
  // 새로고침하면 쌓았던 칸의 주인(시트)이 없으므로 표시를 지운다
  if (window.history.state?.overlay) window.history.replaceState({ ...window.history.state, overlay: 0 }, '')

  window.addEventListener('popstate', (e) => {
    if (ignorePop) return
    const depth = e.state?.overlay ?? 0
    while (stack.length > depth) stack.pop().onBack()
  })

  // 다른 화면으로 넘어가기 전에 쌓인 칸을 치운다 (안 치우면 나중에 뒤로가기를 두 번 눌러야 한다)
  r.beforeEach(async (to, from) => {
    if (to.fullPath !== from.fullPath) await clearBack()
  })
}

// i 번째부터 맨 위까지 치운다. 라우터가 화면 이동으로 여기지 않게 알림을 끄고(false) 뒤로 간다
function dropFrom(i) {
  const n = stack.splice(i).filter((item) => item.pushed).length
  if (!n) return pending
  pending = pending.then(
    () =>
      new Promise((resolve) => {
        ignorePop = true
        const done = () => {
          clearTimeout(timer)
          window.removeEventListener('popstate', done)
          ignorePop = false
          resolve()
        }
        const timer = setTimeout(done, 500) // popstate 가 안 오는 경우 대비
        window.addEventListener('popstate', done)
        router.options.history.go(-n, false)
      }),
  )
  return pending
}

function clearBack() {
  return dropFrom(0)
}

export function pushBack(onBack) {
  const item = { onBack, pushed: false }
  stack.push(item)
  const depth = stack.length
  // 앞의 칸을 치우는 중이면 끝난 뒤에 쌓는다 (그 사이 닫혔으면 안 쌓는다)
  pending = pending.then(() => {
    if (!stack.includes(item)) return
    window.history.pushState({ ...window.history.state, overlay: depth }, '')
    item.pushed = true
  })
  return function release() {
    const i = stack.indexOf(item)
    return i < 0 ? pending : dropFrom(i) // i < 0: 뒤로가기로 이미 치워졌다
  }
}

export async function goUp(r, target) {
  await clearBack()
  const back = window.history.state?.back
  if (back && (back === target || (!target.includes('?') && back.split('?')[0] === target))) r.back()
  else r.replace(target)
}
