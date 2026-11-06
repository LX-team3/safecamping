package com.campin.member;

import java.util.List;

import jakarta.validation.constraints.NotNull;

// 여행 취향 저장 요청. 고른 code 들 (빈 목록이면 모두 지움). 개수·값 검사는 ActivityService 에서
public record ActivityRequest(
        @NotNull(message = "여행 취향을 보내 주세요.")
        List<String> activities
) {
}
