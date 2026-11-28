package com.campin.brief;

// 브리핑 요청 본문: { "survey": { "who": ["child"], "kid": "baby", "site": "auto", "weak": ["일산화탄소", "화재"] } }
// survey 는 화면이 localStorage 의 웹 1분 진단 결과에서 꺼내 보낸다. 진단을 안 했으면 null 이거나 본문 없이 보내도 된다
// 한 칸을 감싼 이유: 나중에 요청 값이 늘어나도(예: 데크 번호) 주소 · 모양을 바꾸지 않으려고
public record BriefRequest(BriefSurvey survey) {
}
