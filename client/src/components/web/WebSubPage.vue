<script setup>
// 웹 안쪽 화면 공통 틀: 위에 어두운 초록 배너(제목) + 아래 본문 (헤더·푸터는 WebLayout 이 씌운다)
// 사용: <WebSubPage eyebrow="내 정보" title="…" desc="…">본문</WebSubPage>   (배너 오른쪽 버튼은 #actions)
defineProps({
  eyebrow: { type: String, default: '' },
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  narrow: { type: Boolean, default: false }, // true 면 본문 폭을 800px 로 좁힌다
})
</script>

<template>
  <div class="subpage">
    <section class="banner">
      <img src="/web/banner-night.png" alt="" aria-hidden="true" />
      <span class="shade" aria-hidden="true"></span>
      <div class="inner">
        <div class="texts">
          <span v-if="eyebrow" class="step">{{ eyebrow }}</span>
          <h1>{{ title }}</h1>
          <p v-if="desc" class="desc">{{ desc }}</p>
        </div>
        <div class="actions"><slot name="actions" /></div>
      </div>
    </section>
    <main class="main">
      <div class="wrap" :class="{ narrow }"><slot /></div>
    </main>
  </div>
</template>

<style scoped>
.banner {
  position: relative;
  background: var(--web-green);
  overflow: hidden;
}
.banner img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 60%;
}
.shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(12, 31, 9, 0.82) 0%, rgba(12, 31, 9, 0.55) 45%, rgba(12, 31, 9, 0.25) 100%);
}
.inner {
  position: relative;
  max-width: 1120px;
  margin: 0 auto;
  padding: 44px 80px 38px;
  box-sizing: border-box;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}
.texts {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
.step {
  padding: 5px 12px;
  border-radius: 999px;
  background: var(--web-white);
  color: var(--web-green);
  font-size: 13px;
  font-weight: 700;
}
h1 {
  margin: 0;
  color: var(--web-white);
  font-size: 38px;
  font-weight: 800;
  letter-spacing: -1px;
}
.desc {
  margin: 0;
  color: var(--web-text-light);
  font-size: 15px;
}
.actions {
  display: flex;
  gap: 8px;
}
.main {
  background: var(--web-bg-booking);
  padding: 36px 20px 80px;
  min-height: 50vh;
}
.wrap {
  max-width: 1120px;
  margin: 0 auto;
}
.wrap.narrow {
  max-width: 800px;
}
</style>
