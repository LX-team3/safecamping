package com.campin.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

// DB 없이 "내 유형 기준" 정렬(sort=bonus)과 가산점 칸만 확인한다 (mapper 는 메모리 목록)
class CampServiceBonusTest {

    private static Camp camp(long id, String name, Integer score, Integer erMin) {
        Camp c = new Camp();
        c.setId(id);
        c.setName(name);
        c.setCampType("auto");
        c.setSafetyScore(score);
        c.setErDriveMin(erMin);
        return c;
    }

    // 실제 데이터 모양: 햇살(45점, 응급실 31분) 은 기본 순서로 마의태자(46점, 51분) 뒤지만 가산점을 더하면 앞선다
    private final List<Camp> camps = List.of(
            camp(1, "마의태자", 46, 51),
            camp(2, "햇살", 45, 31),
            camp(3, "아이호", 0, 78),
            camp(4, "점수없음", null, 20));

    private final CampService service = new CampService(new CampMapper() {
        @Override public List<Camp> findCamps(String sido, String sigungu, String campType) { return camps; }
        @Override public Camp findById(Long id) { return null; }
        @Override public List<CampFacilityRow> findFacilities(Long campId) { return List.of(); }
        @Override public List<CampTourRow> findTours(Long campId) { return List.of(); }
    }, null);

    @Test
    void 기본_정렬은_안전점수_순() {
        assertEquals(List.of("마의태자", "햇살", "아이호", "점수없음"),
                service.list(null, null, null, "score").stream().map(CampResponse::name).toList());
    }

    @Test
    void 가산점_정렬은_안전점수_더하기_가산점_순이고_점수는_그대로() {
        List<CampResponse> list = service.list(null, null, null, "bonus");

        assertEquals(List.of("햇살", "마의태자", "아이호", "점수없음"), list.stream().map(CampResponse::name).toList());
        CampResponse sunny = list.get(0);
        assertEquals(45, sunny.safetyScore());   // 저장된 점수 · 등급은 바뀌지 않는다
        assertEquals("caution", sunny.grade());
        assertEquals(5, sunny.erBonus());          // 31분 → 5 × 29/30 = 4.83 → 5
        assertEquals(2, list.get(1).erBonus());    // 51분 → 2
        assertEquals(0, list.get(2).erBonus());    // 78분 → 0
    }
}
