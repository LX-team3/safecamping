package com.campin.alert;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 행정안전부 긴급재난문자(재난안전데이터 공유플랫폼 DSSP-IF-00247) 응답 JSON → Alert 목록
// 실제 응답으로 확인함 (2026-11-09):
//   성공 {"header":{"resultMsg":"NORMAL SERVICE","resultCode":"00","errorMsg":null},"numOfRows":3,"pageNo":1,"totalCount":63888,
//         "body":[{"MSG_CN":"...","RCPTN_RGN_NM":"경기도 김포시 ","CRT_DT":"2023/09/19 12:22:17","EMRG_STEP_NM":"안전안내",
//                  "SN":205355,"DST_SE_NM":"기타","REG_YMD":"2023-09-19","MDFCN_YMD":"2023-09-19", ...ID 칸들}]}
//   오류 {"header":{"resultCode":"30"(키) 또는 "32"(등록 안 된 IP),"resultMsg":"...","errorMsg":"..."},"body":null}
//   소문자 낙타 표기(msgCn 등)도 받게 둔 것은 혹시 응답 모양이 바뀔 때를 대비한 것
public final class DisasterMessageParser {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    // 칸 이름 후보 (앞에 있는 것부터 찾는다)
    private static final String[] FIELD_ID = {"SN", "sn"};
    private static final String[] FIELD_MESSAGE = {"MSG_CN", "msgCn", "msg_cn"};
    private static final String[] FIELD_REGION = {"RCPTN_RGN_NM", "rcptnRgnNm", "rcptn_rgn_nm"};
    private static final String[] FIELD_TIME = {"CRT_DT", "crtDt", "crt_dt", "REG_YMD", "regYmd"};
    private static final String[] FIELD_CATEGORY = {"DST_SE_NM", "dstSeNm", "dst_se_nm"};

    // 성공 코드. 데이터 없음 코드는 확인 전이라 흔히 쓰는 03 도 성공(빈 목록)으로 본다
    private static final Set<String> OK_CODES = Set.of("00", "0", "03");

    // 시각 표기 후보 (예: 2026/11/09 14:26:21 — 공유플랫폼 웹 목록에 보이는 모양)
    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    // 수신 지역 이름의 시·도 → 알림 화면의 시·도 이름 (Sido.label)
    private static final Map<String, String> SIDO_NAMES = Map.ofEntries(
            Map.entry("서울특별시", "서울"), Map.entry("서울", "서울"),
            Map.entry("부산광역시", "부산"), Map.entry("부산", "부산"),
            Map.entry("대구광역시", "대구"), Map.entry("대구", "대구"),
            Map.entry("인천광역시", "인천"), Map.entry("인천", "인천"),
            Map.entry("광주광역시", "광주"), Map.entry("광주", "광주"),
            Map.entry("대전광역시", "대전"), Map.entry("대전", "대전"),
            Map.entry("울산광역시", "울산"), Map.entry("울산", "울산"),
            Map.entry("세종특별자치시", "세종"), Map.entry("세종", "세종"),
            Map.entry("경기도", "경기"), Map.entry("경기", "경기"),
            Map.entry("강원특별자치도", "강원"), Map.entry("강원도", "강원"), Map.entry("강원", "강원"),
            Map.entry("충청북도", "충북"), Map.entry("충북", "충북"),
            Map.entry("충청남도", "충남"), Map.entry("충남", "충남"),
            Map.entry("전북특별자치도", "전북"), Map.entry("전라북도", "전북"), Map.entry("전북", "전북"),
            Map.entry("전라남도", "전남"), Map.entry("전남", "전남"),
            Map.entry("경상북도", "경북"), Map.entry("경북", "경북"),
            Map.entry("경상남도", "경남"), Map.entry("경남", "경남"),
            Map.entry("제주특별자치도", "제주"), Map.entry("제주도", "제주"), Map.entry("제주", "제주"));

    // 전국에 보낸 문자: 어느 지역을 골라도 보이게 AlertService 가 따로 처리한다
    public static final String NATIONWIDE = "전국";

    private DisasterMessageParser() {
    }

    // 응답이 오류 코드면 IllegalStateException → 클라이언트가 더미로 대신한다
    public static List<Alert> parse(String json) {
        JsonNode root = JSON.readTree(json);
        JsonNode header = root.path("header");
        String code = text(header, "resultCode");
        if (code != null && !OK_CODES.contains(code)) {
            throw new IllegalStateException(code + " " + text(header, "resultMsg"));
        }

        List<Alert> alerts = new ArrayList<>();
        for (JsonNode item : items(root.path("body"))) {
            String message = first(item, FIELD_MESSAGE);
            OffsetDateTime at = time(first(item, FIELD_TIME));
            if (message == null || at == null) {
                continue; // 내용이나 시각이 없으면 보여 줄 수 없다
            }
            if (isMissingPerson(message)) {
                continue; // 실종자 찾기 문자는 캠핑 안전 알림이 아니라 빼기로 함 (2026-11-09 결정)
            }
            String id = first(item, FIELD_ID);
            String category = first(item, FIELD_CATEGORY);
            if (category == null) {
                category = "재난문자";
            }
            // 한 문자가 여러 시·도로 갔으면 시·도마다 한 건씩 만든다 (지역을 골라 볼 때 각 지역에 보이게)
            for (Map.Entry<String, String> region : regions(first(item, FIELD_REGION)).entrySet()) {
                String key = id != null ? id : Integer.toHexString((message + at).hashCode());
                alerts.add(new Alert("sms-" + key + "-" + region.getKey(), "disaster", region.getKey(),
                        region.getValue(), category, message, at, false));
            }
        }
        return alerts;
    }

    // body 가 배열이거나, {items: [...]} / {items: {item: [...]}} 이거나, 한 건짜리 객체여도 받는다
    private static List<JsonNode> items(JsonNode body) {
        if (body.isMissingNode() || body.isNull()) {
            return List.of();
        }
        if (body.isArray()) {
            List<JsonNode> list = new ArrayList<>();
            body.forEach(list::add);
            return list;
        }
        if (body.has("items")) {
            JsonNode items = body.path("items");
            return items(items.has("item") ? items.path("item") : items);
        }
        return body.isObject() ? List.of(body) : List.of();
    }

    // 실종자 찾기 문자: 경찰청이 "OOO씨를 찾습니다 … ☎182" 모양으로 보낸다 (재난 구분은 "기타")
    // "찾습니다" 와 함께 실종·배회·182(경찰 실종 신고 번호)가 있으면 실종자 문자로 본다
    static boolean isMissingPerson(String message) {
        return message.contains("찾습니다")
                && (message.contains("실종") || message.contains("배회") || message.contains("182"));
    }

    // "강원특별자치도 인제군,강원특별자치도 홍천군" → {강원: "강원특별자치도 인제군, 강원특별자치도 홍천군"}
    // 시·도를 알 수 없는 지역은 "전국"으로 모은다 (예: "전국")
    static Map<String, String> regions(String raw) {
        Map<String, List<String>> bySido = new LinkedHashMap<>();
        if (raw == null) {
            bySido.computeIfAbsent(NATIONWIDE, k -> new ArrayList<>()).add(NATIONWIDE);
        } else {
            for (String part : raw.split("[,，]")) {
                String area = part.strip();
                if (area.isEmpty()) {
                    continue;
                }
                String sido = SIDO_NAMES.getOrDefault(area.split("\\s+")[0], NATIONWIDE);
                bySido.computeIfAbsent(sido, k -> new ArrayList<>()).add(area);
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        bySido.forEach((sido, areas) -> result.put(sido, String.join(", ", areas)));
        return result;
    }

    static OffsetDateTime time(String raw) {
        if (raw == null) {
            return null;
        }
        for (DateTimeFormatter format : TIME_FORMATS) {
            try {
                return LocalDateTime.parse(raw.strip(), format).atZone(KST).toOffsetDateTime();
            } catch (DateTimeParseException ignored) {
                // 다음 표기로 다시 시도
            }
        }
        return null;
    }

    private static String first(JsonNode item, String[] names) {
        for (String name : names) {
            String value = text(item, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String s = value.asString().strip();
        return s.isEmpty() ? null : s;
    }
}
