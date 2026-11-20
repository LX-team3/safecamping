<script setup>
// 웹 후기 쓰기 · 수정 팝업 (내 예약 → 지난 캠핑 카드의 "후기 쓰기" / "수정하기")
// 사용: <WebReviewModal :open="…" :camp-name="…" :sub="날짜" :review="기존 후기 또는 null" :busy :error @save="…" @cancel="…" />
// 별점은 넣지 않는다 (팀 결정). 사진 올리기 동의 체크도 뺐다. 글이나 사진 중 하나만 있어도 저장된다
// 사진은 두 종류가 섞인다: 서버에 이미 있는 사진 { id, url } / 이번에 고른 사진 { file, url(미리보기) }
// save 로 { content, keepPhotoIds, files } 를 넘긴다 → 서버 POST /api/member/camp-plans/{id}/review 의 칸과 같다
// 서버 규칙(CampReviewService)과 같은 검사: 글 1000자 · 사진 9장 · 한 장 10MB · 이미지 파일만
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import WebConfirmModal from '@/components/web/WebConfirmModal.vue'

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
const asking = ref(false) // "작성 중인 후기가 사라져요" 확인 중
const fileInput = ref(null)

const isEdit = computed(() => !!props.review)
const canSave = computed(() => (content.value.trim().length > 0 || photos.value.length > 0) && !props.busy)

// 새로 고른 사진의 미리보기 주소는 브라우저 메모리를 쓰므로 다 쓰면 돌려준다 (서버 사진 주소는 그대로)
function release(list) {
  list.filter((p) => p.file).forEach((p) => URL.revokeObjectURL(p.url))
}

// 팝업이 열릴 때마다 처음 값으로 채운다 (수정이면 기존 후기)
watch(
  () => props.open,
  (v) => {
    release(photos.value)
    photos.value = []
    if (!v) return
    content.value = props.review?.content || ''
    photos.value = (props.review?.photos || []).map((p) => ({ id: p.id, url: p.url }))
    notice.value = ''
    asking.value = false
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
  if (dirty.value) asking.value = true
  else emit('cancel')
}
function save() {
  if (!canSave.value) return
  emit('save', {
    content: content.value.trim(),
    keepPhotoIds: photos.value.filter((p) => p.id != null).map((p) => p.id),
    files: photos.value.filter((p) => p.file).map((p) => p.file),
  })
}

const onKey = (e) => {
  if (props.open && !asking.value && e.key === 'Escape') close()
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  release(photos.value)
})
</script>

<template>
  <div v-if="open" class="dim" @click.self="close">
    <div class="box" role="dialog" aria-modal="true" aria-labelledby="web-review-title">
      <h2 id="web-review-title">{{ campName }}</h2>
      <p v-if="sub" class="sub">{{ sub }}</p>

      <div class="textbox">
        <textarea v-model="content" :maxlength="MAX_LEN" aria-label="후기"
          placeholder="캠핑을 다녀오면서 느낀 후기를 알려 주세요"></textarea>
        <span class="count">{{ content.length }}/{{ MAX_LEN }}</span>
      </div>

      <div class="photos">
        <button type="button" class="upload" :disabled="photos.length >= MAX_PHOTOS || busy" @click="fileInput.click()">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M9 3 7.2 5H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-3.2L15 3H9zm3 15a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-2a3 3 0 1 0 0-6 3 3 0 0 0 0 6z" /></svg>
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

      <div class="foot">
        <button type="button" class="cancel" :disabled="busy" @click="close">취소</button>
        <button type="button" class="ok" :disabled="!canSave" @click="save">{{ busy ? '저장 중...' : isEdit ? '수정하기' : '작성하기' }}</button>
      </div>
    </div>
    <WebConfirmModal :open="asking" :title="isEdit ? '수정을 그만둘까요?' : '작성을 그만둘까요?'"
      :text="isEdit ? '바꾼 내용은 저장되지 않아요.' : '작성 중인 후기가 사라져요.'"
      confirm-label="그만두기" cancel-label="계속 쓰기" @confirm="asking = false; emit('cancel')" @cancel="asking = false" />
  </div>
</template>

<style scoped>
.dim {
  position: fixed;
  inset: 0;
  z-index: 40; /* 확인 팝업(WebConfirmModal, 50)이 이 위에 뜨도록 낮게 */
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--web-dim);
}
.box {
  width: 520px;
  max-width: 100%;
  max-height: 90vh;
  overflow-y: auto;
  padding: 28px 24px 20px;
  box-sizing: border-box;
  border-radius: 16px;
  background: var(--web-bg);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.3);
}
h2 {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.4px;
  color: var(--web-text);
}
h2:has(+ .sub) {
  margin-bottom: 4px;
}
.sub {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--web-text-sub);
}
.textbox {
  position: relative;
  padding: 14px 14px 30px;
  border-radius: 12px;
  background: var(--web-bg-field);
}
textarea {
  width: 100%;
  height: 170px;
  border: 0;
  outline: none;
  resize: none;
  background: transparent;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  color: var(--web-text);
}
textarea::placeholder {
  color: var(--web-placeholder);
}
.count {
  position: absolute;
  right: 14px;
  bottom: 10px;
  font-size: 12px;
  color: var(--web-text-mute);
}
.photos {
  margin: 20px 0 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}
.upload {
  flex: 0 0 auto;
  width: 96px;
  height: 96px;
  border: 1px solid var(--web-line-input);
  border-radius: 10px;
  background: var(--web-bg);
  color: var(--web-text-sub);
  font-family: inherit;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
}
.upload:hover:not(:disabled) {
  border-color: var(--web-green);
}
.upload:disabled {
  opacity: 0.5;
  cursor: default;
}
.sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
.previews {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.pv {
  position: relative;
  width: 64px;
  height: 64px;
}
.pv img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
  background: var(--web-photo-empty);
}
.rm {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 50%;
  background: var(--web-text);
  color: var(--web-white);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}
.hint {
  font-size: 15px;
  font-weight: 600;
  color: var(--web-text);
}
.notice {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--web-terracotta);
}
.foot {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--web-line);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.cancel,
.ok {
  min-width: 88px;
  height: 44px;
  padding: 0 18px;
  border-radius: 8px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.cancel {
  border: 1px solid var(--web-line-ghost);
  background: var(--web-bg);
  color: var(--web-text-sub);
}
.ok {
  border: 0;
  background: var(--web-green);
  color: var(--web-white);
}
.cancel:disabled,
.ok:disabled {
  opacity: 0.4;
  cursor: default;
}
</style>
