package com.campin.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.NotFoundException;

// DB 없이 위치 조회 규칙만 확인한다 (mapper 는 메모리 값으로 대신함, 가족방은 FakeFamily)
// FakeFamily: mom(1)·dad(2) 는 1번 방, other(3) 는 2번 방, alone 은 방 없음
class LocationServiceTest {

    private LocationService service;

    @BeforeEach
    void setUp() {
        // 방 번호별로 SQL 이 돌려줄 줄을 정해 둔다 (실제 SQL 은 family_member 로 방 사람만 고른다)
        Map<Long, List<FamilyLocation>> byRoom = Map.of(
                1L, List.of(location(1L, "엄마", 128.17, 38.0699), location(2L, "아빠", 128.1706, 38.0695)),
                2L, List.of(location(3L, "남", 127.0, 37.5)));
        LocationMapper fakeMapper = roomId -> byRoom.getOrDefault(roomId, List.of());
        service = new LocationService(fakeMapper, FakeFamily.service());
    }

    private static FamilyLocation location(Long memberId, String name, double lon, double lat) {
        FamilyLocation l = new FamilyLocation();
        l.setMemberId(memberId);
        l.setName(name);
        l.setLon(lon);
        l.setLat(lat);
        return l;
    }

    @Test
    void 같은_가족방_사람들의_위치가_보이고_나는_me_로_표시된다() {
        List<LocationResponse> list = service.findAll("dad");

        assertEquals(List.of("엄마", "아빠"), list.stream().map(LocationResponse::name).toList());
        assertFalse(list.get(0).me());
        assertTrue(list.get(1).me());  // dad 로 로그인 → 아빠가 나
    }

    @Test
    void 경도는_lng_로_위도는_lat_로_보낸다() {
        LocationResponse mom = service.findAll("mom").get(0);

        assertEquals(128.17, mom.lng());
        assertEquals(38.0699, mom.lat());
    }

    @Test
    void 다른_가족방의_위치는_보이지_않는다() {
        List<LocationResponse> list = service.findAll("other");

        assertEquals(List.of("남"), list.stream().map(LocationResponse::name).toList());
    }

    @Test
    void 가족방이_없으면_위치를_볼_수_없다() {
        assertThrows(NotFoundException.class, () -> service.findAll("alone"));
    }
}
