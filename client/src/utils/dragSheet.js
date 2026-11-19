// 아래 시트(지도 위에 올라오는 판)를 손잡이로 끌어 올리고 내리는 도우미
// 차박 · 내 주변 · 탐색 화면이 같이 쓴다. 움직임 모양(애니메이션)은 styles/base.css 의 .drag-sheet · .drag-handle
//
// 쓰는 법:
//   const snaps = computed(() => ({ min: 120, open: 430 }))   // 단계 이름 → 보이는 높이(px)
//   const sheet = useDragSheet(snaps, 'open')
//   <section class="drag-sheet" :class="{ dragging: sheet.dragging.value }" :style="sheet.style.value">
//     <button class="drag-handle" v-on="sheet.events">...</button>
//
// 손잡이를 끌면 손가락을 바로 따라오고, 놓으면 가장 가까운 단계로 부드럽게 붙는다
// 그냥 누르면(키보드 Enter 포함) 한 단계 위로, 맨 위에서는 한 단계 아래로
//
// 끊기지 않게: 시트 높이는 가장 높은 단계로 고정해 두고, 위치(transform)만 아래로 밀어 보이는 높이를 바꾼다
// (높이를 바꾸면 매 장면 안쪽 배치를 다시 계산해서 끊긴다. transform 은 GPU 가 처리)
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const MOVE_PX = 6 // 이만큼 움직여야 "끌기"로 본다 (손가락 떨림은 누르기로)

export function useDragSheet(snaps, initial) {
  const snap = ref(initial) // 지금 단계 이름
  const dragH = ref(null) // 끄는 중이면 그 높이(px), 아니면 null
  const height = computed(() => dragH.value ?? snaps.value[snap.value]) // 지금 보이는 높이
  const dragging = computed(() => dragH.value !== null)
  const maxHeight = computed(() => Math.max(...Object.values(snaps.value)))
  // 시트에 붙일 style: 높이는 고정, 보이지 않는 만큼 아래로 민다
  const style = computed(() => ({
    height: `${maxHeight.value}px`,
    transform: `translate3d(0, ${maxHeight.value - height.value}px, 0)`,
  }))

  // 낮은 단계부터 높은 단계 순서
  const order = () => Object.keys(snaps.value).sort((a, b) => snaps.value[a] - snaps.value[b])

  function tap() {
    const names = order()
    const i = names.indexOf(snap.value)
    snap.value = i < names.length - 1 ? names[i + 1] : names[Math.max(0, names.length - 2)]
  }

  let startY = 0
  let startH = 0
  let moved = false

  const events = {
    pointerdown(e) {
      startY = e.clientY
      startH = height.value
      moved = false
      e.currentTarget.setPointerCapture(e.pointerId) // 손가락이 손잡이 밖으로 나가도 계속 따라간다
    },
    pointermove(e) {
      if (!e.currentTarget.hasPointerCapture(e.pointerId)) return
      const dy = e.clientY - startY
      if (Math.abs(dy) > MOVE_PX) moved = true
      if (!moved) return
      const values = Object.values(snaps.value)
      dragH.value = Math.min(Math.max(...values), Math.max(Math.min(...values), startH - dy))
    },
    pointerup() {
      if (!moved) {
        tap()
        return
      }
      // 놓은 높이에서 가장 가까운 단계로 붙는다
      const h = dragH.value
      snap.value = order().reduce((best, name) =>
        Math.abs(snaps.value[name] - h) < Math.abs(snaps.value[best] - h) ? name : best)
      dragH.value = null
      moved = false
    },
    pointercancel() {
      dragH.value = null
      moved = false
    },
    // 키보드(Enter·Space)로 누른 경우만 여기서 처리 (마우스·손가락은 pointerup 에서 이미 처리)
    click(e) {
      if (e.detail === 0) tap()
    },
  }

  return { snap, height, dragging, style, events }
}

// 요소의 높이(px)를 계속 재서 돌려준다 (창 크기·내용이 바뀌면 따라 바뀜)
export function useElementHeight(elRef, initial = 0) {
  const h = ref(initial)
  let observer = null
  onMounted(() => {
    observer = new ResizeObserver(() => {
      if (elRef.value) h.value = elRef.value.offsetHeight
    })
    if (elRef.value) observer.observe(elRef.value)
  })
  onBeforeUnmount(() => observer?.disconnect())
  return h
}
