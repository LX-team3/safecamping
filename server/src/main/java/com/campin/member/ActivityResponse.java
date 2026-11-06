package com.campin.member;

import java.util.Arrays;
import java.util.List;

// 여행 취향 화면에 필요한 것: 선택지 6개 + 내가 고른 code 들 + 최대 개수
public record ActivityResponse(List<Option> options, List<String> selected, int max) {

    public record Option(String code, String label, String desc) {
    }

    public static ActivityResponse of(List<String> selected) {
        List<Option> options = Arrays.stream(ActivityOption.values())
                .map(o -> new Option(o.code(), o.label(), o.desc()))
                .toList();
        return new ActivityResponse(options, selected, ActivityOption.MAX);
    }
}
