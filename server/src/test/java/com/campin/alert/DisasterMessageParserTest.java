package com.campin.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

// 긴급재난문자 응답 → Alert
class DisasterMessageParserTest {

    @Test
    void 실제_응답_모양을_읽는다() {
        // 2026-11-09 실제 응답 모양. SN 은 숫자, 지역 끝에 공백이 붙어 온다
        List<Alert> alerts = DisasterMessageParser.parse("""
                {"header":{"resultMsg":"NORMAL SERVICE","resultCode":"00","errorMsg":null},
                 "numOfRows":3,"pageNo":1,"totalCount":4,
                 "body":[{"MSG_CN":"09:47분경 보령시 주교면 주택 화재 발생. 인근 주민들은 화재 현장 접근을 자제하시고 안전에 유의하시기 바랍니다. [보령시]",
                          "RCPTN_RGN_NM":"충청남도 보령시 ","CRT_DT":"2026/11/09 10:19:48","EMRG_STEP_ID":"4372",
                          "REG_YMD":"2026/11/09 10:20:25.000000000","DST_SE_ID":"15","RCPTN_RGN_ID":"227",
                          "EMRG_STEP_NM":"안전안내","SN":270013,"DST_SE_NM":"화재","MDFCN_YMD":"2026/11/09 10:54:11.000000000"}]}
                """);
        assertEquals(1, alerts.size());
        Alert a = alerts.get(0);
        assertEquals("sms-270013-충남", a.id());
        assertEquals("충남", a.sido());
        assertEquals("충청남도 보령시", a.area());
        assertEquals("화재", a.category());
        assertEquals(10, a.issuedAt().getHour());
    }

    @Test
    void 실종자_찾기_문자는_뺀다() {
        // 실제 응답에 섞여 오는 실종자 문자 모양 (이름·인상착의는 지움)
        List<Alert> alerts = DisasterMessageParser.parse("""
                {"header":{"resultCode":"00"},
                 "body":[{"SN":205355,"CRT_DT":"2023/09/19 12:22:17","DST_SE_NM":"기타","RCPTN_RGN_NM":"경기도 김포시 ",
                          "MSG_CN":"[경기남부경찰청] 김포시에서 배회중인 OOO씨(남,40세)를 찾습니다 / ☎182"},
                         {"SN":205356,"CRT_DT":"2023/09/19 13:30:45","DST_SE_NM":"기타","RCPTN_RGN_NM":"서울특별시 노원구 ",
                          "MSG_CN":"[서울경찰청] 노원구에서 실종된 OOO씨(여,41세)를 찾습니다 / ☎182"},
                         {"SN":270015,"CRT_DT":"2026/11/09 14:26:21","DST_SE_NM":"화재","RCPTN_RGN_NM":"충청북도 음성군 대소읍",
                          "MSG_CN":"오늘 대소읍 아파트 화재는 14:17부로 완전 진화되어 안전조치가 완료되었음을 알려드립니다.[음성군]"}]}
                """);
        assertEquals(List.of("충북"), alerts.stream().map(Alert::sido).toList());
        assertTrue(DisasterMessageParser.isMissingPerson("[서울경찰청] 용산구에서 배회중인 OOO씨를 찾습니다 / ☎182"));
        assertFalse(DisasterMessageParser.isMissingPerson("산불 위험이 높습니다. 입산 시 화기를 가지고 가지 마십시오."));
    }

    @Test
    void 여러_시도로_간_문자는_시도마다_한_건() {
        List<Alert> alerts = DisasterMessageParser.parse("""
                {"header":{"resultCode":"00"},
                 "body":[{"SN":270015,"CRT_DT":"2026/11/09 14:26:21",
                          "MSG_CN":"오늘 밤 강풍 예상. 시설물 관리에 유의하세요.",
                          "RCPTN_RGN_NM":"강원특별자치도 인제군,강원특별자치도 홍천군,경기도 가평군",
                          "DST_SE_NM":"강풍"}]}
                """);

        assertEquals(2, alerts.size());
        Alert gangwon = alerts.get(0);
        assertEquals("강원", gangwon.sido());
        assertEquals("강원특별자치도 인제군, 강원특별자치도 홍천군", gangwon.area());
        assertEquals("disaster", gangwon.kind());
        assertEquals("강풍", gangwon.category());
        assertEquals("sms-270015-강원", gangwon.id());
        assertEquals("경기", alerts.get(1).sido());
        assertEquals(14, gangwon.issuedAt().getHour()); // 한국 시간 그대로
    }

    @Test
    void 소문자_칸_이름이나_items_감싼_모양도_받는다() {
        List<Alert> alerts = DisasterMessageParser.parse("""
                {"header":{"resultCode":"00"},
                 "body":{"items":{"item":{"sn":"1","crtDt":"2026-11-09 08:00:00",
                          "msgCn":"한파 주의","rcptnRgnNm":"서울특별시 종로구"}}}}
                """);
        assertEquals(1, alerts.size());
        assertEquals("서울", alerts.get(0).sido());
        assertEquals("재난문자", alerts.get(0).category()); // 재난 구분이 없으면 기본 이름
    }

    @Test
    void 내용이나_시각이_없는_문자는_건너뛴다() {
        List<Alert> alerts = DisasterMessageParser.parse("""
                {"header":{"resultCode":"00"},
                 "body":[{"SN":1,"MSG_CN":"시각 없음","RCPTN_RGN_NM":"부산광역시"},
                         {"SN":2,"CRT_DT":"2026/11/09 10:00:00","RCPTN_RGN_NM":"부산광역시"}]}
                """);
        assertTrue(alerts.isEmpty());
    }

    @Test
    void 데이터가_없으면_빈_목록() {
        assertTrue(DisasterMessageParser.parse("{\"header\":{\"resultCode\":\"00\"},\"body\":[]}").isEmpty());
        assertTrue(DisasterMessageParser.parse("{\"header\":{\"resultCode\":\"00\"},\"body\":null}").isEmpty());
    }

    @Test
    void 오류_코드면_예외_더미로_대신하게() {
        // 실제로 확인한 응답: 등록되지 않은 IP
        assertThrows(IllegalStateException.class, () -> DisasterMessageParser.parse("""
                {"header":{"resultMsg":"UNREGISTERED IP ERROR","resultCode":"32","errorMsg":"등록되지 않은 IP"},"body":null}
                """));
        assertThrows(IllegalStateException.class, () -> DisasterMessageParser.parse("""
                {"header":{"resultMsg":"SERVICE KEY IS NOT REGISTERED ERROR","resultCode":"30"},"body":null}
                """));
    }

    @Test
    void 지역_이름을_시도로() {
        Map<String, String> r = DisasterMessageParser.regions("전라북도 전주시, 전북특별자치도 군산시,제주도 서귀포시");
        assertEquals(List.of("전북", "제주"), List.copyOf(r.keySet()));
        assertEquals("전국", DisasterMessageParser.regions("전국").keySet().iterator().next());
        assertEquals("전국", DisasterMessageParser.regions(null).keySet().iterator().next());
    }

    @Test
    void 여러_시각_표기를_읽는다() {
        assertNotNull(DisasterMessageParser.time("2026/11/09 14:26:21"));
        assertNotNull(DisasterMessageParser.time("2026-11-09 14:26:21"));
        assertNotNull(DisasterMessageParser.time("20261109142621"));
        assertEquals(null, DisasterMessageParser.time("어제"));
    }
}
