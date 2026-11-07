package com.campin.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

// 기상청 특보현황 t6 글 → 시·도별 알림 변환 확인
class KmaWarningParserTest {

    // 2026-11-08 에 실제로 받은 응답의 t6 그대로
    private static final String REAL_T6 = "o 건조주의보 : 강원도(동해평지, 삼척평지, 속초평지, 속초산지, 고성평지, 고성산지, "
            + "양양평지, 양양산지, 강릉평지, 강릉산지), 경상북도(울진평지)";

    @Test
    void 실제_응답을_시도별로_나눈다() {
        List<Alert> alerts = KmaWarningParser.parse(REAL_T6, "202611080200");
        assertEquals(2, alerts.size());

        Alert gangwon = alerts.get(0);
        assertEquals("강원", gangwon.sido());
        assertEquals("건조주의보", gangwon.category());
        assertEquals("warning", gangwon.kind());
        assertTrue(gangwon.active());
        assertEquals("강원 동해평지, 삼척평지, 속초평지 외 7곳", gangwon.area());
        assertTrue(gangwon.message().contains("강릉산지"));  // 내용에는 구역 전체
        assertEquals(OffsetDateTime.parse("2026-11-08T02:00+09:00"), gangwon.issuedAt());

        Alert gyeongbuk = alerts.get(1);
        assertEquals("경북", gyeongbuk.sido());
        assertEquals("경북 울진평지", gyeongbuk.area());
    }

    @Test
    void 없음이면_빈_목록() {
        assertTrue(KmaWarningParser.parse("o 없음\r\n\r\n", "202611080200").isEmpty());
        assertTrue(KmaWarningParser.parse(null, "202611080200").isEmpty());
    }

    @Test
    void 여러_종류는_줄마다_나뉘고_바다_구역은_뺀다() {
        String t6 = "o 강풍주의보 : 경기도(연천, 포천), 서해5도\r\n"
                + "o 풍랑주의보 : 동해중부안쪽먼바다, 제주도남쪽먼바다\r\n"
                + "o 한파주의보 : 충청북도(제천, 단양), 울릉도.독도";
        List<Alert> alerts = KmaWarningParser.parse(t6, "202611080200");

        assertEquals(List.of("경기", "인천", "충북", "경북"), alerts.stream().map(Alert::sido).toList());
        assertTrue(alerts.stream().noneMatch(a -> a.category().equals("풍랑주의보")));
    }

    @Test
    void 긴_항목이_다음_줄로_넘어가도_이어_붙인다() {
        String t6 = "o 한파주의보 : 강원도(철원, 화천),\r\n경기도(연천)";
        assertEquals(List.of("강원", "경기"), KmaWarningParser.parse(t6, "202611080200").stream().map(Alert::sido).toList());
    }

    @Test
    void 시도_이름_바꾸기() {
        assertEquals("전남", KmaWarningParser.toSido("전라남도"));
        assertEquals("제주", KmaWarningParser.toSido("제주도"));
        assertEquals("서울", KmaWarningParser.toSido("서울"));
        assertNull(KmaWarningParser.toSido("남해서부앞바다"));
    }

    @Test
    void 기준_시각_형식이_다르면_실패로_처리() {
        assertThrows(RuntimeException.class, () -> KmaWarningParser.parse(REAL_T6, "2026-11-08"));
    }
}
