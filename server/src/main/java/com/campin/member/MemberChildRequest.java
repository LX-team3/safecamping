package com.campin.member;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// PUT /api/members/me/children 요청: 아이 1, 아이 2 ... 순서대로 나이대
// 예: { "ageGroups": ["preschool", "infant"] } — 빈 배열이면 아이 정보를 지운다
public record MemberChildRequest(
        @NotNull(message = "아이 나이대가 없습니다.")
        @Size(max = 10, message = "아이는 10명까지 저장할 수 있어요.")
        List<String> ageGroups
) {
}
