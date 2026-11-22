<script setup>
// 웹(PC) 하단 (설계 ⑧-0 일러스트 + ⑧ 하단): 데이터 출처 · 만든 사람
import WebIllust from './WebIllust.vue'
</script>

<template>
  <div class="web-footer">
    <WebIllust />

    <footer class="foot">
      <div class="inner">
        <img src="/web/logo.png" alt="CAMPINE" class="logo" />
        <dl>
          <dt>데이터 출처</dt>
          <dd>
            <span>캠핑장 · 한국관광공사 고캠핑 · 관광정보 API, 강원특별자치도 인제군 등록 야영장 현황</span>
            <span>안전시설 · 공공데이터포털 (응급실 · 소방서 · 경찰서 · 약국 · 편의점 · 가로등 · CCTV), 행정안전부 대피시설</span>
            <span>날씨 · 지도 · 기상청 단기예보 · 기상특보, 카카오맵</span>
          </dd>
          <dt>만든 사람</dt>
          <dd>공간정보아카데미 3조 안전삼각대 — 이용효 · 배기환 · 서유림 · 한준서 · 정민영</dd>
        </dl>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.web-footer {
  margin-top: 100px;
}
.foot {
  background: var(--web-bg-soft);
  border-top: 2px solid var(--web-green);
}
.inner {
  padding: 52px 80px 56px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 14px;
  color: var(--web-text-sub);
}
.logo {
  height: 30px;
  width: auto;
  display: block;
  align-self: flex-start;
}
dl {
  margin: 10px 0 0;
  display: grid;
  grid-template-columns: 92px 1fr;
  column-gap: 20px;
  row-gap: 8px;
  line-height: 1.6;
}
dt {
  font-weight: 700;
  color: var(--web-text);
}
dd {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
</style>
