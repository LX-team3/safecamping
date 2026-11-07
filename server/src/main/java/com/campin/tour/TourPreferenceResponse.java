package com.campin.tour;

import java.util.List;

// 회원이 고른 여행 취향 (선택지 code 들)
public record TourPreferenceResponse(List<String> activity, List<String> nature) {
}
