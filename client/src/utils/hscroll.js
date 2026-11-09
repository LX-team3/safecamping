// v-hscroll: 가로로 넘기는 칩 줄을 마우스 휠로도 넘길 수 있게 하는 지시어
// 휴대폰은 손가락으로 밀면 되지만, PC 마우스 휠은 위아래로만 움직여서 옆 칩을 볼 방법이 없다
// → 칩 줄 위에서 휠을 굴리면 그 만큼 옆으로 넘긴다. 더 넘길 곳이 없으면 원래대로(화면 위아래 스크롤) 둔다
// 쓰는 법: <script setup> 에서 import { vHscroll } from '@/utils/hscroll' 후 <div v-hscroll>
function onWheel(e) {
  const el = e.currentTarget
  if (el.scrollWidth <= el.clientWidth) return // 넘칠 만큼 길지 않으면 아무것도 안 함
  if (Math.abs(e.deltaX) >= Math.abs(e.deltaY)) return // 터치패드 옆 밀기는 브라우저가 알아서 처리
  const atStart = el.scrollLeft <= 0
  const atEnd = el.scrollLeft + el.clientWidth >= el.scrollWidth - 1
  if ((e.deltaY < 0 && atStart) || (e.deltaY > 0 && atEnd)) return // 끝에 닿으면 화면 스크롤로 넘겨준다
  el.scrollLeft += e.deltaY
  e.preventDefault()
}

export const vHscroll = {
  // passive: false — preventDefault 로 화면이 같이 위아래로 움직이지 않게 하려면 필요
  mounted: (el) => el.addEventListener('wheel', onWheel, { passive: false }),
  unmounted: (el) => el.removeEventListener('wheel', onWheel),
}
