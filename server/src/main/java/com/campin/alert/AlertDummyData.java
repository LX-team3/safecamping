package com.campin.alert;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

// 알림 더미 — 실제 기상특보·재난문자가 아니다 (화면에 "더미 데이터"로 표시됨)
// 시각은 "지금"에서 몇 분 전으로 만들어 늘 최근 알림처럼 보이게 한다
// API 키를 받으면: 기상특보(기상청)·재난문자(행정안전부)를 서버가 주기적으로 받아 DB 에 쌓고 이 파일은 지운다
public final class AlertDummyData {

    private AlertDummyData() {
    }

    public static List<Alert> all() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
        return List.of(
                warning("w1", "강원", "강원 산지", "호우주의보", "강원 산지에 호우주의보가 발효 중입니다. 계곡·하천변 야영을 피하고 물이 불어나면 바로 높은 곳으로 이동하세요.", now.minusMinutes(40), true),
                disaster("d1", "강원", "강원 홍천군", "호우", "오늘 밤 시간당 30mm 이상의 강한 비가 예상됩니다. 계곡·하천 야영객은 안전한 곳으로 대피하시기 바랍니다.", now.minusMinutes(25)),
                disaster("d2", "강원", "강원 평창군", "한파", "오늘 밤부터 기온이 크게 떨어질 것으로 예상됩니다. 난방기 사용 시 환기와 화재 예방에 유의하시기 바랍니다.", now.minusMinutes(130)),
                warning("w2", "경기", "경기 북부", "한파주의보", "경기 북부에 한파주의보가 발효 중입니다. 텐트 안에서 연료를 태우는 난방은 일산화탄소 위험이 있습니다.", now.minusMinutes(90), true),
                disaster("d3", "경기", "경기 가평군", "산불", "건조한 날씨로 산불 위험이 높습니다. 야외에서 불 피우기와 쓰레기 태우기를 하지 마십시오.", now.minusMinutes(55)),
                disaster("d4", "전남", "전남 담양군", "한파", "오늘 밤부터 기온이 크게 떨어질 것으로 예상됩니다. 외출 시 따뜻한 옷차림으로 건강관리에 유의하세요.", now.minusMinutes(70)),
                disaster("d5", "경북", "경북 문경시", "한파", "오늘 밤부터 기온이 크게 떨어질 것으로 예상됩니다. 난방기 사용 시 화재 예방에 유의하세요.", now.minusMinutes(80)),
                disaster("d6", "경남", "경남 진주시", "산불", "산불 예방은 시민 여러분의 관심과 실천에서 시작됩니다. 입산 시 화기를 가지고 가지 마십시오.", now.minusMinutes(95)),
                warning("w3", "제주", "제주 산지", "강풍주의보", "제주 산지에 내려졌던 강풍주의보가 해제되었습니다.", now.minusHours(20), false),
                disaster("d7", "서울", "서울 종로구", "교통", "도로 공사로 오늘 22시부터 일부 구간 통행이 제한됩니다. 우회 도로를 이용하시기 바랍니다.", now.minusMinutes(150)));
    }

    private static Alert warning(String id, String sido, String area, String category, String message,
                                 OffsetDateTime at, boolean active) {
        return new Alert(id, "warning", sido, area, category, message, at, active);
    }

    private static Alert disaster(String id, String sido, String area, String category, String message,
                                  OffsetDateTime at) {
        return new Alert(id, "disaster", sido, area, category, message, at, false);
    }
}
