package com.campin.alert;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 기상청 특보현황(getPwnStatus)의 t6 글을 Alert 목록으로 바꾼다
// t6 예: "o 건조주의보 : 강원도(동해평지, 삼척평지), 경상북도(울진평지)\r\no 한파주의보 : 경기도(연천, 포천)"
//   → 한 줄 = 특보 종류 하나, 쉼표 = 시·도 하나, 괄호 안 = 그 시·도의 세부 구역
// 네트워크를 쓰지 않는 순수 변환이라 따로 떼어 두었다 (테스트하기 쉽게)
final class KmaWarningParser {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TM = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final int AREA_SHOW = 3;  // 목록 제목에는 세부 구역을 3곳까지만 쓰고 "외 n곳"

    // 기상청 구역 이름 앞부분 → 우리 시·도 이름(Sido label). 위에서부터 차례로 비교한다
    private static final Map<String, String> PREFIX = new LinkedHashMap<>();
    static {
        for (String same : List.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "제주")) {
            PREFIX.put(same, same);
        }
        PREFIX.put("충청북도", "충북");
        PREFIX.put("충북", "충북");
        PREFIX.put("충청남도", "충남");
        PREFIX.put("충남", "충남");
        PREFIX.put("전라북도", "전북");
        PREFIX.put("전북", "전북");
        PREFIX.put("전라남도", "전남");
        PREFIX.put("전남", "전남");
        PREFIX.put("경상북도", "경북");
        PREFIX.put("경북", "경북");
        PREFIX.put("경상남도", "경남");
        PREFIX.put("경남", "경남");
        PREFIX.put("울릉도", "경북");  // "울릉도.독도"
        PREFIX.put("독도", "경북");
        PREFIX.put("서해5도", "인천");
    }

    private KmaWarningParser() {
    }

    static List<Alert> parse(String t6, String tmEf) {
        if (t6 == null || t6.isBlank()) {
            return List.of();
        }
        OffsetDateTime at = parseTime(tmEf);

        // 종류|시·도 → 세부 구역들. 같은 종류가 여러 줄에 나뉘어 와도 한 시·도는 한 건으로 합친다
        Map<String, List<String>> zones = new LinkedHashMap<>();
        for (String entry : entries(t6)) {
            int colon = entry.indexOf(':');
            if (colon < 0) {
                continue;  // "없음" 처럼 특보가 없는 줄
            }
            String category = entry.substring(0, colon).strip();
            for (String token : splitTopLevel(entry.substring(colon + 1))) {
                int open = token.indexOf('(');
                String name = (open < 0 ? token : token.substring(0, open)).strip();
                String sido = toSido(name);
                if (sido == null) {
                    continue;  // 바다 구역(풍랑특보 등)이나 모르는 이름은 시·도 알림에 넣지 않는다
                }
                List<String> list = zones.computeIfAbsent(category + "|" + sido, k -> new ArrayList<>());
                if (open >= 0 && token.endsWith(")")) {
                    for (String z : token.substring(open + 1, token.length() - 1).split(",")) {
                        if (!z.isBlank()) {
                            list.add(z.strip());
                        }
                    }
                }
            }
        }

        List<Alert> alerts = new ArrayList<>();
        zones.forEach((key, list) -> {
            String category = key.substring(0, key.indexOf('|'));
            String sido = key.substring(key.indexOf('|') + 1);
            String all = list.isEmpty() ? sido : sido + " " + String.join(", ", list);
            String message = all + "에 " + category + "가 발효 중입니다. (기상청 특보 현황 기준)";
            alerts.add(new Alert("kma-" + tmEf + "-" + category + "-" + sido, "warning", sido, shortArea(sido, list),
                    category, message, at, true));
        });
        return alerts;
    }

    // "o " 로 시작하는 줄이 한 항목. 긴 항목이 다음 줄로 넘어가면 앞 항목에 이어 붙인다
    private static List<String> entries(String t6) {
        List<String> entries = new ArrayList<>();
        for (String line : t6.split("\\R")) {
            String s = line.strip();
            if (s.isEmpty()) {
                continue;
            }
            if (s.startsWith("o ")) {
                entries.add(s.substring(2).strip());
            } else if (!entries.isEmpty()) {
                int last = entries.size() - 1;
                entries.set(last, entries.get(last) + " " + s);
            }
        }
        return entries;
    }

    // 괄호 밖의 쉼표로만 나눈다. "강원도(동해평지, 삼척평지), 경상북도(울진평지)" → 2개
    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                parts.add(cur.toString().strip());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.toString().isBlank()) {
            parts.add(cur.toString().strip());
        }
        return parts;
    }

    static String toSido(String name) {
        if (name.contains("바다")) {
            return null;  // 앞바다·먼바다 = 바다 구역
        }
        for (Map.Entry<String, String> e : PREFIX.entrySet()) {
            if (name.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String shortArea(String sido, List<String> zones) {
        if (zones.isEmpty()) {
            return sido;
        }
        if (zones.size() <= AREA_SHOW) {
            return sido + " " + String.join(", ", zones);
        }
        return sido + " " + String.join(", ", zones.subList(0, AREA_SHOW)) + " 외 " + (zones.size() - AREA_SHOW) + "곳";
    }

    // "202611080200" → 2026-11-08T02:00+09:00
    // 형식이 다르면 예외 → 호출한 쪽이 "받기 실패"로 처리한다 (기준 시각을 지어내지 않기 위해)
    static OffsetDateTime parseTime(String tm) {
        return LocalDateTime.parse(tm, TM).atZone(KST).toOffsetDateTime();
    }
}
