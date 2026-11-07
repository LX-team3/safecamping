package com.campin.tour;

import java.util.List;

import jakarta.validation.constraints.NotNull;

// 여행 취향 저장 요청. 빈 목록이면 그 질문의 답을 지운다. 개수·값 검사는 TourPreferenceService 에서
public record TourPreferenceRequest(
        @NotNull(message = "가고 싶은 관광지 · 체험을 보내 주세요.")
        List<String> activity,
        @NotNull(message = "자연환경을 보내 주세요.")
        List<String> nature
) {
}
