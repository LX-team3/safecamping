package com.campin.nearby;

// 내 주변 지도 · "지도에 보일 것" 시트의 안전 시설 켜기/끄기 목록. 화면은 이 목록을 API 로 받아 그린다
// code 는 facility.type 과 같은 값 (지도 마커를 고를 때 비교한다)
// ready=false: 아직 데이터를 넣지 않은 시설 (약국·대피소) → 화면에 줄은 보이지만 켜도 지도에 아무것도 안 나온다
// 공중화장실(toilet)은 내 주변 지도에서 빼기로 함 (2026-11-08) — facility 테이블에는 그대로 둔다
public enum NearbyLayer {
    ER("er", "+", "응급실", "야간 진료 여부 함께 표시", true),
    FIRE("fire", "119", "소방서 · 119안전센터", "구급차 출동 기준 거리", true),
    POLICE("police", "P", "경찰서 · 지구대", "캠핑장 주변 10km", true),
    STORE("store", "24", "24시 편의점", "밤에 필요한 물품", true),
    PHARMACY("pharmacy", "약", "약국", "데이터 준비 중", false),
    SHELTER("shelter", "↗", "대피소", "재난 시 대피 장소 · 데이터 준비 중", false);

    private final String code;
    private final String icon;   // 줄 왼쪽 동그라미 안 글씨
    private final String label;
    private final String desc;
    private final boolean ready;

    NearbyLayer(String code, String icon, String label, String desc, boolean ready) {
        this.code = code;
        this.icon = icon;
        this.label = label;
        this.desc = desc;
        this.ready = ready;
    }

    public String code() { return code; }
    public String icon() { return icon; }
    public String label() { return label; }
    public String desc() { return desc; }
    public boolean ready() { return ready; }
}
