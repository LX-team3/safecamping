package com.campin.alert;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// 시·도 17곳과 대략의 중심 좌표 (위도·경도)
// 임시: 내 위치 → 가장 가까운 시·도 중심으로 지역을 정한다. 경계 근처에서는 틀릴 수 있다
// 실제로는 카카오 "좌표 → 행정구역" API 로 바꾼다 (카카오 API 연결 때)
public enum Sido {
    SEOUL("서울", 37.566, 126.978), BUSAN("부산", 35.180, 129.075), DAEGU("대구", 35.871, 128.601),
    INCHEON("인천", 37.456, 126.705), GWANGJU("광주", 35.160, 126.851), DAEJEON("대전", 36.350, 127.385),
    ULSAN("울산", 35.539, 129.311), SEJONG("세종", 36.480, 127.289), GYEONGGI("경기", 37.275, 127.009),
    GANGWON("강원", 37.885, 127.730), CHUNGBUK("충북", 36.636, 127.491), CHUNGNAM("충남", 36.659, 126.673),
    JEONBUK("전북", 35.820, 127.109), JEONNAM("전남", 34.816, 126.463), GYEONGBUK("경북", 36.576, 128.506),
    GYEONGNAM("경남", 35.238, 128.692), JEJU("제주", 33.489, 126.498);

    private final String label;
    private final double lat;
    private final double lon;

    Sido(String label, double lat, double lon) {
        this.label = label;
        this.lat = lat;
        this.lon = lon;
    }

    public String label() { return label; }

    public static List<String> labels() {
        return Arrays.stream(values()).map(Sido::label).toList();
    }

    public static boolean isValid(String label) {
        return Arrays.stream(values()).anyMatch(s -> s.label.equals(label));
    }

    // 가장 가까운 시·도. 좁은 범위라 위도·경도 차이를 거리처럼 써도 순서를 정하는 데는 충분하다
    public static String nearest(double lat, double lon) {
        double lonScale = Math.cos(Math.toRadians(lat));  // 경도 1도는 위도 1도보다 짧다
        return Arrays.stream(values())
                .min(Comparator.comparingDouble(s -> Math.pow(s.lat - lat, 2) + Math.pow((s.lon - lon) * lonScale, 2)))
                .orElseThrow().label;
    }
}
