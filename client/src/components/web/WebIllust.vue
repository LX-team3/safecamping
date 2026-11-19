<script setup>
// 하단·결과 화면 위쪽에 까는 캠핑 풍경 일러스트 (장식). 색은 웹 토큰 변수로 칠한다
</script>

<template>
  <div class="art" aria-hidden="true">
      <svg width="100%" height="120" viewBox="0 0 1280 120" preserveAspectRatio="xMidYMax meet" fill="none"
        stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M20 120 L80 34 L140 120 Z" class="tint" />
        <path d="M68 51 L80 34 L92 51 L86 56 L80 51 L74 56 Z" class="white" />
        <path d="M112 120 L150 64 L188 120 Z" class="tint2" />
        <circle cx="222" cy="86" r="15" class="tint" /><path d="M222 101 V120 M222 108 l-6 -6 M222 112 l6 -6" />
        <circle cx="256" cy="96" r="10" class="tint2" /><path d="M256 106 V120" />
        <path d="M314 70 V120" />
        <path d="M314 76 H346 L354 84 L346 92 H314 Z" class="accent" />
        <path d="M314 98 H286 L279 105 L286 112 H314 Z" class="white" />
        <path d="M430 48 a12 12 0 0 1 22 -6 a10 10 0 0 1 18 6 a8 8 0 0 1 -2 16 h-36 a8 8 0 0 1 -2 -16 Z" class="white" />
        <path d="M560 30 a8 8 0 0 1 15 -4 a7 7 0 0 1 12 4 a5 5 0 0 1 -1 10 h-24 a5 5 0 0 1 -2 -10 Z" class="white" />
        <path d="M960 38 a10 10 0 0 1 18 -5 a8 8 0 0 1 15 5 a6 6 0 0 1 -1 12 h-30 a6 6 0 0 1 -2 -12 Z" class="white" />
        <path d="M1050 24 l3 7 7 3 -7 3 -3 7 -3 -7 -7 -3 7 -3 Z" class="accent nostroke" />
        <path d="M1210 18 l2 5 5 2 -5 2 -2 5 -2 -5 -5 -2 5 -2 Z" class="green nostroke" />
        <path d="M1140 30 V42" />
        <path d="M1080 120 L1140 42 L1200 120 Z" class="accent" />
        <path d="M1122 120 L1140 80 L1158 120 Z" class="green" />
        <path d="M1094 104 L1072 120 M1186 104 L1208 120" />
        <path d="M1228 118 L1262 108 M1228 108 L1262 118" />
        <path d="M1245 106 c-9 -7 -6 -15 -1 -22 c1 7 9 8 7 16 c-1 4 -3 6 -6 6 Z" class="accent" />
      </svg>
  </div>
</template>

<style scoped>
.art {
  padding: 0 80px;
  line-height: 0;
}
.art svg {
  stroke: var(--web-green);
}
.art .tint { fill: var(--web-tint); }
.art .tint2 { fill: var(--web-tint-2); }
.art .white { fill: var(--web-white); }
.art .accent { fill: var(--web-terracotta); }
.art .green { fill: var(--web-green); }
.art .nostroke { stroke: none; }
</style>
