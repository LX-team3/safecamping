package com.campin.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;

// DB 없이 지역 서비스 규칙만 확인한다
class RegionServiceTest {

    private String lastQuery;

    private final RegionService service = new RegionService(new RegionMapper() {
        @Override public List<SidoResponse> findSidos() { return List.of(new SidoResponse(10L, "강원특별자치도", 18)); }
        @Override public boolean existsSido(Long sidoId) { return sidoId == 10L; }
        @Override public List<SigunguResponse> findSigungus(Long sidoId) {
            return List.of(new SigunguResponse(1L, "강원특별자치도", "인제군", true));
        }
        @Override public List<SigunguResponse> searchSigungus(String q) {
            lastQuery = q;
            return List.of();
        }
    });

    @Test
    void 시도의_시군구를_준다() {
        assertEquals("인제군", service.sigungus(10L).get(0).getName());
    }

    @Test
    void 없는_시도는_오류() {
        assertThrows(InvalidRequestException.class, () -> service.sigungus(99L));
    }

    @Test
    void 빈_검색어는_찾지_않고_앞뒤_공백은_지운다() {
        assertTrue(service.search("  ").isEmpty());
        assertEquals(null, lastQuery);
        service.search(" 인제 ");
        assertEquals("인제", lastQuery);
    }
}
