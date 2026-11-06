package com.campin.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;

// 알림 규칙 확인 (더미 데이터 기준)
class AlertServiceTest {

    private final AlertService service = new AlertService();

    @Test
    void 지역을_주면_그_지역_알림만_최신순으로() {
        AlertResponse res = service.find("강원", null, null);
        assertEquals("강원", res.sido());
        assertTrue(res.dummy());
        assertTrue(res.items().stream().allMatch(a -> a.sido().equals("강원")));
        for (int i = 1; i < res.items().size(); i++) {
            assertFalse(res.items().get(i).issuedAt().isAfter(res.items().get(i - 1).issuedAt()));
        }
        assertEquals(1, res.activeWarnings().size());  // 호우주의보
    }

    @Test
    void 위치를_주면_가장_가까운_시도() {
        assertEquals("강원", service.find(null, 37.69, 127.89).sido());  // 홍천 근처
        assertEquals("제주", service.find(null, 33.36, 126.53).sido());  // 한라산 근처
    }

    @Test
    void 아무것도_없으면_전국() {
        AlertResponse res = service.find(null, null, null);
        assertNull(res.sido());
        assertEquals(17, res.regions().size());
    }

    @Test
    void 해제된_특보는_발효_중에_넣지_않는다() {
        assertTrue(service.find("제주", null, null).activeWarnings().isEmpty());
    }

    @Test
    void 없는_지역이나_해외_위치는_400() {
        assertThrows(InvalidRequestException.class, () -> service.find("뉴욕", null, null));
        assertThrows(InvalidRequestException.class, () -> service.find(null, 40.7, -74.0));
    }
}
