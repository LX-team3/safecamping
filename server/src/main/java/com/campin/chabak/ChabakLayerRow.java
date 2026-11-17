package com.campin.chabak;

import lombok.Data;

// 차박 지도 레이어 한 줄 (DB 에서 받은 그대로). 면이면 geojson, 점이면 lat·lng
@Data  // lombok: getter·setter 를 만들어 준다 (MyBatis 가 setter 로 값을 채움)
public class ChabakLayerRow {
    private Long id;          // 등록 야영장이면 camp.id (캠핑장 상세로 갈 때), 나머지는 null
    private String name;      // 구역 이름 · 지번 · 야영장 이름
    private Double lat;
    private Double lng;
    private String geojson;   // 구역 경계 GeoJSON 글 (점 레이어는 null)
    private Integer score;    // 안전점수 (야영장은 camp.safety_score, 후보지는 서비스가 계산해 넣음)
    private String area;      // 목록 둘째 줄: 야영장은 "인제군 북면", 후보지는 소유 구분 (군유지 · 국유지)
    private String imageUrl;  // 야영장 사진 (camp.image_url). 후보지는 null → 화면이 스카이뷰로 대신
    private Integer erDriveMin; // 응급실까지 차로 몇 분: 야영장은 camp.er_drive_min, 후보지는 직선거리 어림 (서비스가 넣음)
}
