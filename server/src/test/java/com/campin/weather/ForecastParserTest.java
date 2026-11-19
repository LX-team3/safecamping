package com.campin.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

// 기상청을 부르지 않고 응답 항목 → 하루치 예보 변환과 격자 변환만 확인한다
class ForecastParserTest {

    private static ForecastParser.Item item(String cat, String date, String time, String value) {
        return new ForecastParser.Item(cat, date, time, value);
    }

    @Test
    void 격자_변환은_기상청_공개_값과_같다() {
        // 서울 → (60, 127), 부산 → (98, 76) : 기상청 동네예보 격자 목록에 있는 대표 값
        assertEquals(new KmaGrid.Point(60, 127), KmaGrid.of(37.5665, 126.9780));
        assertEquals(new KmaGrid.Point(98, 76), KmaGrid.of(35.1796, 129.0756));
    }

    @Test
    void 하루_단위로_묶고_최저_최고는_TMN_TMX를_우선한다() {
        List<DayForecast> days = ForecastParser.parse(List.of(
                item("TMP", "20261119", "0600", "-12"), item("TMP", "20261119", "1500", "-1"),
                item("TMN", "20261119", "0600", "-13.0"), item("TMX", "20261119", "1500", "-1.0"),
                item("POP", "20261119", "0900", "20"), item("POP", "20261119", "1500", "30"),
                item("WSD", "20261119", "0900", "4"), item("WSD", "20261119", "1500", "7.2"),
                item("SKY", "20261119", "0900", "1"), item("SKY", "20261119", "1200", "3"),
                item("PTY", "20261119", "0900", "0"),
                item("TMP", "20261120", "0600", "-5"), item("TMP", "20261120", "1500", "4")));

        assertEquals(2, days.size());
        DayForecast d = days.get(0);
        assertEquals(LocalDate.of(2026, 11, 19), d.date());
        assertEquals(-13.0, d.minTemp());
        assertEquals(-1.0, d.maxTemp());
        assertEquals(30, d.pop());              // 그날 가장 높은 값
        assertEquals(7.2, d.maxWind());
        assertEquals("구름많음", d.sky());        // 낮 12시 값
        assertEquals("없음", d.precip());

        DayForecast next = days.get(1);          // TMN·TMX 가 없으면 시간별 기온에서 뽑는다
        assertEquals(-5.0, next.minTemp());
        assertEquals(4.0, next.maxTemp());
        assertNull(next.pop());
    }

    @Test
    void 강수_형태는_그날_처음_나오는_것() {
        List<DayForecast> days = ForecastParser.parse(List.of(
                item("PTY", "20261119", "0900", "0"), item("PTY", "20261119", "1200", "3"),
                item("PTY", "20261119", "1500", "1")));

        assertEquals("눈", days.get(0).precip());
    }

    @Test
    void 숫자가_아닌_값은_건너뛴다() {
        List<DayForecast> days = ForecastParser.parse(List.of(
                item("POP", "20261119", "0900", "강수없음"), item("TMP", "20261119", "0900", "3")));

        assertNull(days.get(0).pop());
        assertEquals(3.0, days.get(0).minTemp());
        assertTrue(days.get(0).hasData());
    }
}
