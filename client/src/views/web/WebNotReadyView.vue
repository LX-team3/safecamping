<script setup>
// 웹(PC) 전용 화면이 아직 없는 주소에서 보이는 안내
</script>

<template>
  <div class="not-ready">
    <h1>PC 화면 준비 중</h1>
    <p>이 화면은 아직 웹(PC)용으로 만들지 않았어요.</p>
  </div>
</template>

<style scoped>
.not-ready {
  padding: 120px 24px;
  text-align: center;
  color: var(--color-text-sub);
}
h1 {
  margin: 0 0 12px;
  font-size: 22px;
  color: var(--color-text);
}
p {
  margin: 0;
  font-size: 15px;
}
</style>
