package com.campin.brief;

import java.util.List;
import java.util.Objects;
import java.util.Set;

// 화면이 브리핑 요청에 담아 보내는 웹 1분 진단 결과 요약 (AI 브리핑 7단계)
// 왜 화면이 보내나: 웹 진단 결과는 서버 DB 에 칸이 없어 브라우저(localStorage, client/src/utils/webSurvey.js)에만 있다
//   → 지도 "내 유형 점수"(needsErBonus)와 같은 방식. 진단을 안 했으면 화면이 null 을 보낸다 (2026-11-28 사용자 결정)
//
//   who  = 누구와: solo(혼자) · group(성인 일행) · child(아이) · senior(어르신)   (webSurvey.js 의 WHO)
//   kid  = 가장 어린 아이 나이: baby(0~6세) · kid(7~12세) · teen(13세 이상) · null  (KID_OPTS)
//   site = 어디서 자는지: glamping(글램핑·카라반) · auto(오토캠핑 텐트) · carcamp(차박) · null  (SITE)
//   weak = 조심할 분야, 약한 순: 일산화탄소 · 화재 · 물·호우  (computeResult 의 weak)
public record BriefSurvey(List<String> who, String kid, String site, List<String> weak) {

    static final Set<String> WHO = Set.of("solo", "group", "child", "senior");
    static final Set<String> KID = Set.of("baby", "kid", "teen");
    static final Set<String> SITE = Set.of("glamping", "auto", "carcamp");
    static final List<String> AXES = List.of("일산화탄소", "화재", "물·호우");

    // 모르는 값은 버린다 (400 으로 막지 않음)
    // 왜: 브라우저에 예전 형식으로 저장된 결과가 남아 있을 수 있다. 진단 일부가 빠져도 브리핑은 일정 · 날씨만으로 만들 수 있다
    // Set.of 는 contains(null) 에서 오류를 내서 null 을 먼저 거른다 (화면이 값을 빼고 보낼 수 있음)
    public BriefSurvey {
        who = who == null ? List.of() : who.stream().filter(Objects::nonNull).filter(WHO::contains).distinct().toList();
        kid = kid != null && KID.contains(kid) ? kid : null;
        site = site != null && SITE.contains(site) ? site : null;
        weak = weak == null ? List.of() : weak.stream().filter(Objects::nonNull).filter(AXES::contains).distinct().toList();
    }

    // 아이 규칙을 쓸지: 아이를 골랐고 12세 이하일 때만 (webSurvey.js 의 hasYoungChild 와 같은 규칙, 팀 결정 2026-11-24)
    boolean youngChild() {
        return who.contains("child") && !"teen".equals(kid);
    }
}
