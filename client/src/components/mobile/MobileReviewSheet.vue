<script setup>
// 앱 후기 쓰기 · 수정 화면 (내 캠핑 → 지난 캠핑 카드의 "후기 쓰기" / "수정하기"). 웹 WebReviewModal 과 같은 규칙
// 사용: <MobileReviewSheet :open="…" :camp-name="…" :sub="날짜" :review="기존 후기 또는 null" :busy :error @save="…" @cancel="…" />
// 별점은 넣지 않는다 (팀 결정). 글이나 사진 중 하나만 있어도 저장된다
// 사진은 두 종류가 섞인다: 서버에 이미 있는 사진 { id, url } / 이번에 고른 사진 { file, url(미리보기) }
// save 로 { content, keepPhotoIds, files } 를 넘긴다 → 서버 POST /api/member/camp-plans/{id}/review 의 칸과 같다
// 서버 규칙(CampReviewService)과 같은 검사: 글 1000자 · 사진 9장 · 한 장 10MB · 이미지 파일만
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const MAX_PHOTOS = 9
const MAX_LEN = 1000
const MAX_BYTES = 10 * 1024 * 1024

const props = defineProps({
  open: { type: Boolean, default: false },
  campName: { type: String, default: '' },
  sub: { type: String, default: '' }, // 이름 밑 작은 글씨 (캠핑 날짜)
  review: { type: Object, default: null }, // 수정이면 서버 후기 { content, photos: [{ id, url }] } — 없으면 새로 쓰기
  busy: { type: Boolean, default: false }, // 저장 중이면 버튼을 잠근다
  error: { type: String, default: '' }, // 서버가 돌려준 실패 문구
})
const emit = defineEmits(['save', 'cancel'])

const content = ref('')
const photos = ref([]) // 화면 순서대로 [{ id, url }] 또는 [{ file, url }]
const notice = ref('')
const fileInput = ref(null)

const isEdit = computed(() => !!props.review)
const canSave = computed(() => (content.value.trim().length > 0 || photos.value.length > 0) && !props.busy)

// 새로 고른 사진의 미리보기 주소는 브라우저 메모리를 쓰므로 다 쓰면 돌려준다 (서버 사진 주소는 그대로)
function release(list) {
  list.filter((p) => p.file).forEach((p) => URL.revokeObjectURL(p.url))
}

// 열릴 때마다 처음 값으로 채운다 (수정이면 기존 후기)
watch(
  () => props.open,
  (v) => {
    release(photos.value)
    photos.value = []
    if (!v) return
    content.value = props.review?.content || ''
    photos.value = (props.review?.photos || []).map((p) => ({ id: p.id, url: p.url }))
    notice.value = ''
  },
)

function pick(e) {
  const picked = [...e.target.files]
  e.target.value = '' // 같은 사진을 다시 골라도 change 가 일어나게
  const images = picked.filter((f) => f.type.startsWith('image/') && f.size <= MAX_BYTES)
  const room = MAX_PHOTOS - photos.value.length
  const msgs = []
  if (images.length < picked.length) msgs.push('10MB가 넘거나 사진이 아닌 파일은 빼고 올렸어요.')
  if (images.length > room) msgs.push(`사진은 ${MAX_PHOTOS}장까지 올릴 수 있어요.`)
  notice.value = msgs.join(' ')
  photos.value = [...photos.value, ...images.slice(0, room).map((file) => ({ file, url: URL.createObjectURL(file) }))]
}
function removeAt(i) {
  release([photos.value[i]])
  photos.value = photos.value.filter((_, k) => k !== i)
  notice.value = ''
}

// 바뀐 게 있는데 닫으려 하면 한 번 묻는다
const dirty = computed(() => {
  const r = props.review
  if (!r) return !!content.value.trim() || photos.value.length > 0
  const before = (r.photos || []).map((p) => p.id)
  return content.value.trim() !== (r.content || '') || photos.value.length !== before.length || photos.value.some((p, i) => p.id !== before[i])
})
function close() {
  if (props.busy) return
  if (dirty.value && !window.confirm(isEdit.value ? '바꾼 내용은 저장되지 않아요. 그만둘까요?' : '작성 중인 후기가 사라져요. 그만둘까요?')) return
  emit('cancel')
}
function save() {
  if (!canSave.value) return
  emit('save', {
    content: content.value.trim(),
    keepPhotoIds: photos.value.filter((p) => p.id != null).map((p) => p.id),
    files: photos.value.filter((p) => p.file).map((p) => p.file),
  })
}

onBeforeUnmount(() => release(photos.value))
</script>

<template>
  <!-- 390px 틀 전체를 덮는 화면. 위 줄의 ×로 닫는다 -->
  <div v-if="open" class="review" role="dialog" aria-modal="true" aria-labelledby="mreview-title">
    <header class="top">
      <button type="button" class="x" aria-label="닫기" :disabled="busy" @click="close">×</button>
      <span class="ttl">{{ isEdit ? '후기 수정' : '후기 쓰기' }}</span>
    </header>

    <div class="body">
      <h2 id="mreview-title">{{ campName }}</h2>
      <p v-if="sub" class="sub">{{ sub }}</p>

      <div class="textbox">
        <textarea v-model="content" :maxlength="MAX_LEN" aria-label="후기"
          placeholder="캠핑을 다녀오면서 느낀 후기를 알려 주세요"></textarea>
        <span class="count">{{ content.length }}/{{ MAX_LEN }}</span>
      </div>

      <div class="photos">
        <button type="button" class="upload" :disabled="photos.length >= MAX_PHOTOS || busy" @click="fileInput.click()">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M9 3 7.2 5H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-3.2L15 3H9zm3 15a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-2a3 3 0 1 0 0-6 3 3 0 0 0 0 6z" /></svg>
          <span>{{ photos.length }}/{{ MAX_PHOTOS }}</span>
          <span class="sr">사진 고르기</span>
        </button>
        <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple hidden @change="pick" />
        <div v-if="photos.length" class="previews">
          <span v-for="(p, i) in photos" :key="p.url" class="pv">
            <img :src="p.url" :alt="`첨부한 사진 ${i + 1}`" />
            <button type="button" class="rm" :disabled="busy" :aria-label="`사진 ${i + 1} 빼기`" @click="removeAt(i)">×</button>
          </span>
        </div>
        <span v-else class="hint">캠핑 사진을 첨부해 보세요</span>
      </div>
      <p v-if="notice" class="notice">{{ notice }}</p>
      <p v-if="error" class="notice" role="alert">{{ error }}</p>
    </div>

    <footer class="foot">
      <button type="button" class="ok" :disabled="!canSave" @click="save">{{ busy ? '저장 중...' : isEdit ? '수정하기' : '작성하기' }}</button>
    </footer>
  </div>
</template>

<style scoped>
.review {
  position: absolute;
  inset: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
}
.top {
  flex: none;
  height: 56px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  border-bottom: 1px solid var(--color-divider);
}
.x {
  width: 44px;
  height: 44px;
  border: 0;
  background: none;
  font-size: 28px;
  line-height: 1;
  color: var(--color-text);
  cursor: pointer;
}
.ttl {
  font-size: 17px;
  font-weight: 700;
}
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
}
h2 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 900;
  letter-spacing: -0.03em;
}
.sub {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--color-text-sub);
}
h2:not(:has(+ .sub)) {
  margin-bottom: 16px;
}
.textbox {
  position: relative;
  padding: 14px 14px 30px;
  border-radius: 12px;
  background: var(--color-bg-soft);
}
textarea {
  width: 100%;
  height: 180px;
  border: 0;
  outline: none;
  resize: none;
  background: transparent;
  font: inherit;
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-text);
}
textarea::placeholder {
  color: var(--color-text-sub);
}
.count {
  position: absolute;
  right: 14px;
  bottom: 10px;
  font-size: 12px;
  color: var(--color-text-sub);
}
.photos {
  margin-top: 20px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.upload {
  flex: none;
  width: 80px;
  height: 80px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-card);
  color: var(--color-text-sub);
  font: inherit;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
}
.upload:disabled {
  opacity: 0.5;
}
.sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
.previews {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.pv {
  position: relative;
  width: 56px;
  height: 56px;
}
.pv img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
  background: var(--color-photo);
}
.rm {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 50%;
  background: var(--color-text);
  color: var(--color-card);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}
.hint {
  font-size: 14px;
  font-weight: 700;
}
.notice {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--color-danger);
}
.foot {
  flex: none;
  padding: 12px 20px calc(16px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid var(--color-divider);
}
.ok {
  width: 100%;
  height: 52px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--color-text);
  color: var(--color-card);
  font: inherit;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.ok:disabled {
  opacity: 0.4;
  cursor: default;
}
.x:focus-visible, .upload:focus-visible, .ok:focus-visible, .rm:focus-visible { outline: 2px solid var(--color-text); outline-offset: 2px; }
</style>
