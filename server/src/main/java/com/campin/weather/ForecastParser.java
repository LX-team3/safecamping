package com.campin.weather;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 기상청 단기예보 응답(시간별 항목 여러 개) → 하루치 DayForecast 목록. 네트워크를 쓰지 않는 순수 계산이라 따로 떼어 두었다
// 항목 하나 = (category, fcstDate, fcstTime, fcstValue). 쓰는 category:
//   TMP 1시간 기온 · TMN 일 최저 · TMX 일 최고 · POP 강수확률 · WSD 풍속 · SKY 하늘 · PTY 강수 형태
final class ForecastParser {

    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private ForecastParser() {
    }

    record Item(String category, String fcstDate, String fcstTime, String fcstValue) {
    }

    static List<DayForecast> parse(List<Item> items) {
        // 날짜별로 모으기 (TreeMap = 날짜 순)
        Map<String, List<Item>> byDate = new TreeMap<>();
        for (Item it : items) {
            if (it.fcstDate() == null || it.category() == null || it.fcstValue() == null) continue;
            byDate.computeIfAbsent(it.fcstDate(), k -> new ArrayList<>()).add(it);
        }
        List<DayForecast> days = new ArrayList<>();
        for (Map.Entry<String, List<Item>> e : byDate.entrySet()) {
            days.add(day(e.getKey(), e.getValue()));
        }
        return days;
    }

    private static DayForecast day(String ymd, List<Item> items) {
        Double tmn = null, tmx = null, tmpMin = null, tmpMax = null, wind = null;
        Integer pop = null;
        String sky = null, skyAny = null, precip = null;

        for (Item it : items) {
            String v = it.fcstValue().strip();
            switch (it.category()) {
                case "TMN" -> tmn = number(v);
                case "TMX" -> tmx = number(v);
                case "TMP" -> {
                    Double t = number(v);
                    if (t != null) {
                        tmpMin = tmpMin == null ? t : Math.min(tmpMin, t);
                        tmpMax = tmpMax == null ? t : Math.max(tmpMax, t);
                    }
                }
                case "POP" -> {
                    Double p = number(v);
                    if (p != null) pop = pop == null ? p.intValue() : Math.max(pop, p.intValue());
                }
                case "WSD" -> {
                    Double w = number(v);
                    if (w != null) wind = wind == null ? w : Math.max(wind, w);
                }
                case "SKY" -> {
                    String name = skyName(v);
                    if (skyAny == null) skyAny = name;
                    if ("1200".equals(it.fcstTime())) sky = name;  // 낮 12시 값을 그날 하늘로
                }
                case "PTY" -> {
                    if (precip == null && !"0".equals(v)) precip = ptyName(v);  // 그날 처음 나오는 강수 형태
                }
                default -> { }
            }
        }
        // 기상청이 준 일 최저·최고(TMN·TMX)가 있으면 그것을, 없으면 시간별 기온에서 뽑는다
        Double min = tmn != null ? tmn : tmpMin;
        Double max = tmx != null ? tmx : tmpMax;
        return new DayForecast(LocalDate.parse(ymd, YMD), min, max, pop, wind,
                sky != null ? sky : skyAny, precip != null ? precip : "없음");
    }

    private static Double number(String v) {
        try {
            return Double.valueOf(v);
        } catch (NumberFormatException e) {
            return null;  // "강수없음" 같은 글자가 오면 건너뛴다
        }
    }

    private static String skyName(String code) {
        return switch (code) {
            case "1" -> "맑음";
            case "3" -> "구름많음";
            case "4" -> "흐림";
            default -> null;
        };
    }

    private static String ptyName(String code) {
        return switch (code) {
            case "1" -> "비";
            case "2" -> "비/눈";
            case "3" -> "눈";
            case "4" -> "소나기";
            default -> "없음";
        };
    }
}
