package com.campin.member;

import java.util.Arrays;

// 여행 취향 선택지 6개. 화면도 이 목록을 API 로 받아 그린다 (선택지는 여기 한곳에서만 고친다)
// code 는 member_activity.activity 에 저장되는 값이라 한 번 정하면 바꾸지 않는다 (이름 label 은 바꿔도 됨)
// 추천 때 이어 붙일 캠핑장 데이터(고캠핑 주변시설·입지·테마)는 desc 에 적어 둔 것을 기준으로 한다
public enum ActivityOption {
    FISHING("fishing", "낚시", "낚시터가 가까운 캠핑장"),
    HIKING("hiking", "등산 · 트레킹", "산책로 · 등산로가 가까운 곳"),
    VALLEY("valley", "계곡 · 물놀이", "계곡 · 강에서 물놀이할 수 있는 곳"),
    BEACH("beach", "바다 · 해수욕", "해변 · 섬 캠핑장"),
    KIDS("kids", "아이 체험", "체험시설 · 놀이시설이 있는 곳"),
    SEASON("season", "계절 풍경", "꽃 · 단풍 · 눈꽃 · 일출 명소");

    public static final int MAX = 3;  // 최대 3개, 순서 없음

    private final String code;
    private final String label;
    private final String desc;

    ActivityOption(String code, String label, String desc) {
        this.code = code;
        this.label = label;
        this.desc = desc;
    }

    public String code() { return code; }
    public String label() { return label; }
    public String desc() { return desc; }

    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(o -> o.code.equals(code));
    }
}
