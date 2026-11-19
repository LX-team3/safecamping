package com.campin.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.NotFoundException;

// DB 없이 위치 조회·저장·삭제 규칙만 확인한다 (mapper 는 메모리 값으로 대신함, 가족방은 FakeFamily)
// FakeFamily: mom(1)·dad(2) 는 1번 방, other(3) 는 2번 방, alone 은 방 없음
class LocationServiceTest {

    // 회원 번호 → 방 번호 (FakeFamily 와 같은 값). 가짜 SQL 이 "이 방 사람"만 고를 때 쓴다
    private static final Map<Long, Long> ROOM_OF = Map.of(1L, 1L, 2L, 1L, 3L, 2L);

    private Map<Long, FamilyLocation> table;  // family_location 테이블 대신 (회원 번호 → 한 줄)
    private LocationService service;

    @BeforeEach
    void setUp() {
        // LinkedHashMap: 넣은 순서 유지 → 실제 SQL 의 "들어온 순서" 정렬 대신
        table = new LinkedHashMap<>();
        table.put(1L, location(1L, "엄마", 128.17, 38.0699));
        table.put(2L, location(2L, "아빠", 128.1706, 38.0695));
        table.put(3L, location(3L, "남", 127.0, 37.5));

        LocationMapper fakeMapper = new LocationMapper() {
            @Override public List<FamilyLocation> findAll(Long roomId) {
                return table.values().stream().filter(l -> roomId.equals(ROOM_OF.get(l.getMemberId()))).toList();
            }
            @Override public int upsert(Long memberId, Double lon, Double lat) {
                FamilyLocation l = table.computeIfAbsent(memberId, id -> location(id, FakeFamily.nameOf(id), lon, lat));
                l.setLon(lon);
                l.setLat(lat);
                return 1;
            }
            @Override public int delete(Long memberId) {
                return table.remove(memberId) == null ? 0 : 1;
            }
        };
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

    @Test
    void 내_위치를_저장하면_lng_는_경도_lon_으로_들어가고_가족에게_보인다() {
        service.saveMine("dad", new LocationRequest(37.5, 127.1));

        assertEquals(127.1, table.get(2L).getLon());
        assertEquals(37.5, table.get(2L).getLat());
        LocationResponse dad = service.findAll("mom").get(1);
        assertEquals(127.1, dad.lng());
        assertFalse(dad.me());  // 엄마 화면에서 아빠는 내가 아니다
    }

    @Test
    void 위치_공유를_끄면_내_줄이_지워져_가족에게_안_보인다() {
        service.deleteMine("dad");

        assertEquals(List.of("엄마"), service.findAll("mom").stream().map(LocationResponse::name).toList());
    }

    @Test
    void 가족방이_없으면_위치를_저장할_수_없다() {
        assertThrows(NotFoundException.class, () -> service.saveMine("alone", new LocationRequest(37.5, 127.1)));
        assertEquals(3, table.size());  // 아무것도 안 들어갔다
    }
}
