package com.campin.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;
import com.campin.place.PlaceInfo;
import com.campin.place.PlaceMapper;
import com.campin.place.PlaceNote;

// DB 없이 관광지 상세 규칙만 확인한다 (mapper 는 고정 값으로 대신함)
class TourServiceTest {

    private final TourMapper fakeMapper = new TourMapper() {
        @Override public Tour findById(Long id) {
            return id == 1L ? new Tour(1L, "자작나무 숲", "자연 · 트레킹", 1, "강원", "인제군", "인제읍", 38.0, 128.2, true) : null;
        }
        @Override public TourFrom findFrom(Long tourId, Long campId) {
            return campId == 1L ? new TourFrom(1L, "설하캠핑장", 38.1, 128.2, 25) : null;
        }
    };
    private final PlaceMapper fakePlace = new PlaceMapper() {
        @Override public List<PlaceInfo> findInfos(String type, Long id) { return List.of(new PlaceInfo("운영", null)); }
        @Override public List<PlaceNote> findNotes(String type, Long id) { return List.of(new PlaceNote("caution", "미끄러워요")); }
    };
    private final TourService service = new TourService(fakeMapper, fakePlace);

    @Test
    void 관광지_상세에_정보_줄과_안내가_붙는다() {
        TourDetailResponse res = service.detail(1L, 1L);
        assertEquals(25, res.from().driveMin());
        assertNull(res.infos().get(0).getValue()); // 값이 없으면 화면에서 "확인 필요"
        assertEquals("caution", res.notes().get(0).getLevel());
    }

    @Test
    void 캠핑장을_안_주면_from_은_비어_있다() {
        assertNull(service.detail(1L, null).from());
    }

    @Test
    void 없는_관광지는_오류() {
        assertThrows(InvalidRequestException.class, () -> service.detail(9L, null));
    }
}
