package com.campin.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;
import com.campin.place.PlaceInfo;
import com.campin.place.PlaceMapper;
import com.campin.place.PlaceNote;

// DB 없이 등급·정렬·추천 규칙만 확인한다 (mapper 는 메모리 목록으로 대신함)
class CampServiceTest {

    private final List<Camp> table = List.of(
            camp(1L, "A", "auto", 90, 17),
            camp(2L, "B", "auto", 88, 21),
            camp(3L, "C", "glamping", 85, 9),
            camp(4L, "D", "auto", 65, 15),
            camp(5L, "E", "backpacking", 35, 30),
            camp(6L, "F", "auto", null, null));

    private String lastSido;
    private String lastSigungu;
    private String lastCampType;
    private CampService service;

    private static Camp camp(Long id, String name, String type, Integer score, Integer er) {
        return new Camp(id, name, "강원특별자치도", "인제군", "북면", type, 38.0, 128.0, score, er, null, true, null);
    }

    @BeforeEach
    void setUp() {
        CampMapper fakeMapper = new CampMapper() {
            @Override public List<Camp> findCamps(String sido, String sigungu, String campType) {
                lastSido = sido;
                lastSigungu = sigungu;
                lastCampType = campType;
                return table.stream().filter(c -> campType == null || c.getCampType().equals(campType)).toList();
            }
            @Override public Camp findById(Long id) {
                return table.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
            }
            @Override public List<CampFacilityRow> findFacilities(Long campId) {
                return List.of(new CampFacilityRow(10L, "fire", "소방서", 38.1, 128.1, 4.0, 5),
                        new CampFacilityRow(11L, "er", "응급실", 38.2, 128.2, 12.9, 17));
            }
            @Override public List<CampTourRow> findTours(Long campId) {
                return List.of(new CampTourRow(2L, "래프팅", "water", "valley", 2, 15, "연령 제한 확인"),
                        new CampTourRow(1L, "자작나무 숲", "trekking", "mountain", 1, 25, null),
                        new CampTourRow(3L, "순위 없음", "food", null, null, 30, null));
            }
        };
        PlaceMapper fakePlace = new PlaceMapper() {
            @Override public List<PlaceInfo> findInfos(String type, Long id) { return List.of(new PlaceInfo(null, "입실 14:00")); }
            @Override public List<PlaceNote> findNotes(String type, Long id) { return List.of(); }
        };
        service = new CampService(fakeMapper, fakePlace);
    }

    private List<String> names(List<CampResponse> camps) {
        return camps.stream().map(CampResponse::name).toList();
    }

    @Test
    void 점수로_등급을_나눈다() {
        assertEquals("safe", CampService.gradeOf(70));
        assertEquals("caution", CampService.gradeOf(69));
        assertEquals("caution", CampService.gradeOf(40));
        assertEquals("danger", CampService.gradeOf(39));
        assertEquals("unknown", CampService.gradeOf(null));
    }

    @Test
    void 기본_정렬은_안전점수_높은_순이고_점수_없는_곳은_맨_뒤() {
        assertEquals(List.of("A", "B", "C", "D", "E", "F"), names(service.list(null, null, null, null)));
    }

    @Test
    void 응급실_가까운_순으로_정렬할_수_있다() {
        assertEquals(List.of("C", "D", "A", "B", "E", "F"), names(service.list(null, null, null, "er")));
    }

    @Test
    void 추천은_위험_등급을_빼고_따로_돌려준다() {
        CampRecommendResponse res = service.recommend(null, null, null, null, null);
        assertEquals(List.of("A", "B", "C", "D", "F"), names(res.camps()));
        assertEquals(List.of("E"), names(res.excluded()));
    }

    @Test
    void 추천은_캠핑_유형이_맞는_곳만_limit_개() {
        CampRecommendResponse res = service.recommend("강원특별자치도", "인제군", "auto", null, 2);
        assertEquals(List.of("A", "B"), names(res.camps()));
        assertEquals("강원특별자치도", lastSido);
        assertEquals("인제군", lastSigungu);
    }

    @Test
    void 모르겠어요_나_빈_값은_유형으로_거르지_않는다() {
        service.list("", " ", "unknown", "");
        assertNull(lastSido);
        assertNull(lastSigungu);
        assertNull(lastCampType);
    }

    @Test
    void 상세에는_시설과_정보_줄이_붙고_시간으로_색이_나뉜다() {
        CampDetailResponse res = service.detail(1L);
        assertEquals("safe", res.grade());
        assertEquals("밤에도 비교적 안심할 수 있어요", res.headline());
        assertEquals("safe", res.facilities().get(0).tone());    // 5분
        assertEquals("caution", res.facilities().get(1).tone()); // 17분
        assertEquals("입실 14:00", res.infos().get(0).getValue());
    }

    @Test
    void 없는_캠핑장은_오류() {
        assertThrows(InvalidRequestException.class, () -> service.detail(99L));
        assertThrows(InvalidRequestException.class, () -> service.tours(99L, null));
    }

    @Test
    void 관광지는_기본이_인기순이고_순위_없는_곳은_맨_뒤() {
        List<String> names = service.tours(1L, null).stream().map(CampTourResponse::name).toList();
        assertEquals(List.of("자작나무 숲", "래프팅", "순위 없음"), names);
    }

    @Test
    void 취향이_맞는_관광지가_앞으로_온다() {
        List<CampTourResponse> tours = service.tours(1L, List.of("valley"));
        assertEquals("래프팅", tours.get(0).name());
        assertEquals(true, tours.get(0).matched());
        assertEquals(false, tours.get(1).matched());
    }

    @Test
    void 잘못된_조건은_오류() {
        assertThrows(InvalidRequestException.class, () -> service.list(null, null, "hotel", null));
        assertThrows(InvalidRequestException.class, () -> service.list(null, null, null, "name"));
        assertThrows(InvalidRequestException.class, () -> service.recommend(null, null, null, null, 0));
    }
}
