package com.campin.facility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;
import com.campin.place.PlaceInfo;
import com.campin.place.PlaceMapper;
import com.campin.place.PlaceNote;

// DB 없이 시설 상세 규칙만 확인한다 (mapper 는 고정 값으로 대신함)
class FacilityServiceTest {

    private final FacilityMapper fakeMapper = new FacilityMapper() {
        @Override public Facility findById(Long id) {
            return id == 3L ? new Facility(3L, "er", "보건의료원", "강원", "인제군", "인제읍", null, null, null, 38.0, 128.0, true) : null;
        }
        @Override public FacilityFrom findFrom(Long facilityId, Long campId) {
            return campId == 1L ? new FacilityFrom(1L, "설하캠핑장", 38.1, 128.2, 12.9, 17) : null;
        }
    };
    private final PlaceMapper fakePlace = new PlaceMapper() {
        @Override public List<PlaceInfo> findInfos(String type, Long id) {
            return type.equals(PlaceMapper.FACILITY) ? List.of(new PlaceInfo("진료", "확인 필요")) : List.of();
        }
        @Override public List<PlaceNote> findNotes(String type, Long id) {
            return type.equals(PlaceMapper.FACILITY) ? List.of(new PlaceNote("danger", "119 먼저")) : List.of();
        }
    };
    private final FacilityService service = new FacilityService(fakeMapper, fakePlace);

    @Test
    void 캠핑장에서_왔으면_거리와_색이_붙는다() {
        FacilityDetailResponse res = service.detail(3L, 1L);
        assertEquals("설하캠핑장", res.from().campName());
        assertEquals(17, res.from().driveMin());
        assertEquals("caution", res.from().tone());
        assertEquals("진료", res.infos().get(0).getLabel());
        assertEquals("danger", res.notes().get(0).getLevel());
    }

    @Test
    void 캠핑장을_안_주거나_연결이_없으면_from_은_비어_있다() {
        assertNull(service.detail(3L, null).from());
        assertNull(service.detail(3L, 2L).from());
    }

    @Test
    void 없는_시설은_오류() {
        assertThrows(InvalidRequestException.class, () -> service.detail(99L, null));
    }
}
