package com.campin.chabak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;

// DB 없이 위치 검사와 응답 모양 옮기기만 확인한다 (mapper 는 정해진 값을 돌려주는 가짜로 대신함)
class ChabakServiceTest {

    // DB 함수가 돌려주는 모양 그대로 (nearbyCamps 는 dist_m, 응답에 없는 필드 extra 도 섞음)
    private static final String DB_JSON = """
            {"pnu": "5181031021100980002", "jibun": "398-2", "jimok": "잡종지", "owner": "국유지",
             "verdict": "주의", "reasons": ["필지 경계 3m 이내"], "zones": [], "nearBoundary": true,
             "nearbyCamps": [{"name": "돌배야영장", "dist_m": 850}],
             "alternatives": [{"jibun": "61-1", "owner": "인제군", "radiusM": 6.5, "distM": 1200,
                               "lat": 38.07, "lng": 128.32}],
             "parcel": {"type": "MultiPolygon", "coordinates": []},
             "extra": 1}
            """;

    private int noZoneCalls = 0; // 불가 구역 SQL 이 몇 번 불렸는지 (한 번만 계산하는지 확인)

    // 인제군 안(위도 37.8~38.3)이면 판정 JSON, 밖이면 null 을 돌려주는 가짜 DB 함수
    private final ChabakService service = new ChabakService(new ChabakMapper() {
        @Override public String findVerdict(double lat, double lng) {
            return lat > 37.8 && lat < 38.3 ? DB_JSON : null;
        }
        @Override public List<ChabakFacility> findFacilities(double lat, double lng) {
            ChabakFacility er = new ChabakFacility();
            er.setType("er");
            er.setName("인제군보건의료원");
            return List.of(er);
        }
        @Override public ChabakLayerRow findNoZone() {
            noZoneCalls++;
            ChabakLayerRow zone = new ChabakLayerRow();
            zone.setName("불가 구역");
            zone.setGeojson("{\"type\": \"MultiPolygon\", \"coordinates\": []}");
            return zone;
        }
        @Override public ChabakLayerRow findCautionZone() {
            return new ChabakLayerRow(); // 주의 구역이 하나도 없을 때 (geojson null)
        }
        @Override public List<ChabakLayerRow> findOpenSpots() { return List.of(); }
        @Override public List<ChabakLayerRow> findCamps() { return List.of(); }
    });

    @Test
    void 레이어는_정해진_종류만_구역은_한_덩어리_JSON_으로_한_번만_계산() {
        assertThrows(InvalidRequestException.class, () -> service.layer("all"));
        assertThrows(InvalidRequestException.class, () -> service.layer(null));
        ChabakLayerItem zone = service.layer("no").get(0);
        assertEquals("불가 구역", zone.name());
        assertEquals("MultiPolygon", zone.geometry().path("type").asString());
        service.layer("no");
        assertEquals(1, noZoneCalls); // 두 번째는 기억해 둔 것
        assertEquals(List.of(), service.layer("caution")); // 구역이 없으면 빈 목록
    }

    @Test
    void 시설도_위치가_빠지면_400_있으면_목록() {
        assertThrows(InvalidRequestException.class, () -> service.facilities(null, 128.32));
        assertEquals("er", service.facilities(38.07, 128.32).get(0).getType());
    }

    @Test
    void 위치가_빠지거나_범위를_벗어나면_400() {
        assertThrows(InvalidRequestException.class, () -> service.verdict(null, 128.32));
        assertThrows(InvalidRequestException.class, () -> service.verdict(38.07, null));
        assertThrows(InvalidRequestException.class, () -> service.verdict(91.0, 128.32));
    }

    @Test
    void 인제군_밖이면_404() {
        assertThrows(NotFoundException.class, () -> service.verdict(37.56, 126.97)); // 서울
    }

    @Test
    void DB_JSON_을_응답_모양으로_옮긴다() {
        ChabakVerdictResponse result = service.verdict(38.078121, 128.324378);
        assertEquals("주의", result.verdict());
        assertEquals("398-2", result.jibun());
        assertEquals(List.of("필지 경계 3m 이내"), result.reasons());
        assertTrue(result.nearBoundary());
        // dist_m → distM 으로 이름이 맞춰진다
        assertEquals(850, result.nearbyCamps().get(0).distM());
        assertEquals(6.5, result.alternatives().get(0).radiusM());
        assertEquals("MultiPolygon", result.parcel().path("type").asString());
    }
}
