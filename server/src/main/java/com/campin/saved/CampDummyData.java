package com.campin.saved;

import java.util.LinkedHashMap;
import java.util.Map;

// 비교 화면용 캠핑장 더미. 값은 설계 파일 Compare.dc.html 에서 그대로 옮겼다 — 실제 거리·점수가 아니다
// 캠핑장 데이터(map 패키지 또는 camp 테이블)가 생기면 그쪽 mapper 로 바꾸고 이 파일은 지운다
public final class CampDummyData {

    // shelterOnFoot: 대피소까지 걸어서 가는 시간이면 true, 차로 가는 시간이면 false
    public record Camp(String id, String name, int safetyScore, int erMinutes, int fireMinutes,
                       int shelterMinutes, boolean shelterOnFoot, boolean hasPower, boolean allowsPet) {
    }

    // 넣은 순서를 지키려고 LinkedHashMap
    private static final Map<String, Camp> CAMPS = new LinkedHashMap<>();

    static {
        add(new Camp("c1", "설하캠핑장", 90, 17, 5, 8, true, true, false));
        add(new Camp("c2", "돌배야영장", 88, 21, 11, 15, true, true, true));
        add(new Camp("c3", "햇살글램핑", 85, 9, 7, 4, false, true, false));
    }

    private static void add(Camp camp) {
        CAMPS.put(camp.id(), camp);
    }

    private CampDummyData() {
    }

    // 없는 id 면 null
    public static Camp find(String id) {
        return CAMPS.get(id);
    }
}
