package com.campin.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;

// 알림 규칙 확인. 기상청은 부르지 않고 WarningSource 자리에 가짜 결과를 넣는다
class AlertServiceTest {

    // 기상청 결과를 못 받은 경우 (키 없음·호출 실패) → 기상특보 더미
    private final AlertService noKma = new AlertService(Optional::empty);

    // 기상청에서 실제로 받은 것처럼: 강원·경북 건조주의보 (2026-11-08 응답 모양)
    private final List<Alert> kmaWarnings = KmaWarningParser.parse(
            "o 건조주의보 : 강원도(동해평지, 삼척평지), 경상북도(울진평지)", "202611080200");
    private final AlertService withKma = new AlertService(() -> Optional.of(kmaWarnings));

    @Test
    void 지역을_주면_그_지역_알림만_최신순으로() {
        AlertResponse res = noKma.find("강원", null, null);
        assertEquals("강원", res.sido());
        assertTrue(res.items().stream().allMatch(a -> a.sido().equals("강원")));
        for (int i = 1; i < res.items().size(); i++) {
            assertFalse(res.items().get(i).issuedAt().isAfter(res.items().get(i - 1).issuedAt()));
        }
        assertEquals(1, res.activeWarnings().size());  // 더미 호우주의보
    }

    @Test
    void 기상청_결과가_없으면_특보는_더미로_표시() {
        AlertResponse res = noKma.find(null, null, null);
        assertTrue(res.warningDummy());
        assertTrue(res.disasterDummy());
    }

    @Test
    void 기상청_결과가_있으면_특보는_실제_재난문자는_아직_더미() {
        AlertResponse res = withKma.find("강원", null, null);
        assertFalse(res.warningDummy());
        assertTrue(res.disasterDummy());
        assertEquals(1, res.activeWarnings().size());
        assertEquals("건조주의보", res.activeWarnings().get(0).category());
        // 더미 기상특보(호우주의보)가 섞이지 않는다
        assertTrue(res.items().stream().filter(a -> a.kind().equals("warning"))
                .allMatch(a -> a.id().startsWith("kma-")));
    }

    @Test
    void 기상청_결과에_특보가_없으면_발효_중도_없다() {
        AlertService empty = new AlertService(() -> Optional.of(List.of()));
        AlertResponse res = empty.find("강원", null, null);
        assertFalse(res.warningDummy());
        assertTrue(res.activeWarnings().isEmpty());
    }

    @Test
    void 위치를_주면_가장_가까운_시도() {
        assertEquals("강원", noKma.find(null, 37.69, 127.89).sido());  // 홍천 근처
        assertEquals("제주", noKma.find(null, 33.36, 126.53).sido());  // 한라산 근처
    }

    @Test
    void 아무것도_없으면_전국() {
        AlertResponse res = noKma.find(null, null, null);
        assertNull(res.sido());
        assertEquals(17, res.regions().size());
    }

    @Test
    void 해제된_특보는_발효_중에_넣지_않는다() {
        assertTrue(noKma.find("제주", null, null).activeWarnings().isEmpty());
    }

    @Test
    void 없는_지역이나_해외_위치는_400() {
        assertThrows(InvalidRequestException.class, () -> noKma.find("뉴욕", null, null));
        assertThrows(InvalidRequestException.class, () -> noKma.find(null, 40.7, -74.0));
    }
}
