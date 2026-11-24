package com.campin.chabak;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.campin.camp.CampService;
import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

// 차박 판정: 지도에서 찍은 지점 → 그 필지가 불가 / 주의 / 제한 없음 / 야영 가능 중 어디인지
// DB 함수가 판정과 JSON 을 다 만들어 주므로, 여기서는 위치 값 검사와 응답 모양으로 옮기기만 한다
@Service
@RequiredArgsConstructor
public class ChabakService {

    // DB 함수에 응답에 없는 필드가 생겨도 오류 없이 건너뛴다
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final ChabakMapper chabakMapper;

    public ChabakVerdictResponse verdict(Double lat, Double lng) {
        checkPosition(lat, lng);
        String json = chabakMapper.findVerdict(lat, lng);
        if (json == null) {
            throw new NotFoundException("차박 판정은 인제군 안에서만 할 수 있습니다.");
        }
        return withCampIds(JSON.readValue(json, ChabakVerdictResponse.class));
    }

    // 근처 야영장에 camp.id 를 붙인다 → 화면에서 누르면 캠핑장 상세로 (이름이 같은 캠핑장이 없으면 id 없이 그대로)
    private ChabakVerdictResponse withCampIds(ChabakVerdictResponse res) {
        List<ChabakVerdictResponse.Camp> camps = res.nearbyCamps();
        if (camps == null || camps.isEmpty()) {
            return res;
        }
        Map<String, Long> ids = new HashMap<>();
        chabakMapper.findCampsByName(camps.stream().map(ChabakVerdictResponse.Camp::name).toList())
                .forEach(r -> ids.putIfAbsent(r.getName(), r.getId()));
        return res.withNearbyCamps(camps.stream()
                .map(c -> new ChabakVerdictResponse.Camp(ids.get(c.name()), c.name(), c.distM()))
                .toList());
    }

    // 찍은 지점의 안전점수: db/merge_to_public.sql 8 단계와 같은 계산 (직선거리, 기준은 raw.score_weights)
    // 주의: 지금 캠핑장 점수(camp.safety_score)는 score/SafetyScoreService 가 카카오 차 시간 + ScoreRules 로 계산해 기준이 다르다
    // 차박 자리 일정의 출발 준비 체크리스트(mycamping/ChecklistLookup)도 이 메서드를 부른다
    //   점수 = Σ(가중치 × 부분점수) ÷ Σ(데이터가 있는 종류의 가중치) × 100
    public ChabakScoreResponse score(Double lat, Double lng) {
        checkPosition(lat, lng);
        List<ChabakScoreRow> rows = chabakMapper.findScoreParts(lat, lng);
        Integer score = scoreOf(rows); // 가중치 합 계산은 scoreOf 한 곳에서만
        if (score == null) {
            String none = CampService.gradeOf(null);
            return new ChabakScoreResponse(null, none, CampService.headlineOf(none), null, List.of());
        }
        List<ChabakScoreResponse.Item> items = rows.stream()
                .map(r -> new ChabakScoreResponse.Item(r.getType(), (int) Math.round(r.getDistM()),
                        (int) Math.round(r.getWeight()), (int) Math.round(r.getPart() * 100)))
                .toList();
        String grade = CampService.gradeOf(score);
        return new ChabakScoreResponse(score, grade, CampService.headlineOf(grade), erDriveMinOf(rows), items);
    }

    // 점수 = Σ(가중치 × 부분점수) ÷ Σ(가중치) × 100 (데이터가 없으면 null) — 찍은 지점 · 차박 후보지가 같이 씀
    static Integer scoreOf(List<ChabakScoreRow> rows) {
        double weightSum = rows.stream().mapToDouble(ChabakScoreRow::getWeight).sum();
        if (rows.isEmpty() || weightSum <= 0) {
            return null;
        }
        double got = rows.stream().mapToDouble(r -> r.getWeight() * r.getPart()).sum();
        return (int) Math.round(got / weightSum * 100);
    }

    // 응급실까지 차로 몇 분 (어림) — 찍은 지점 · 차박 후보지가 같이 씀. 응급실 데이터가 없으면 null
    static Integer erDriveMinOf(List<ChabakScoreRow> rows) {
        return rows.stream().filter(r -> "er".equals(r.getType())).findFirst()
                .map(r -> driveMin(r.getDistM())).orElse(null);
    }

    // 차로 걸리는 시간(분) 추정: 직선거리 × 1.4(굽은 길) ÷ 시속 45km, 최소 1분 (merge 의 pg_temp.drive_min 과 같은 식)
    static int driveMin(double distM) {
        return Math.max(1, (int) Math.ceil(distM * 1.4 / 1000 / 45 * 60));
    }

    // 찍은 지점에서 종류별로 가까운 안전 시설 (응급실·소방서·지구대·편의점·약국 각 3곳)
    public List<ChabakFacility> facilities(Double lat, Double lng) {
        checkPosition(lat, lng);
        return chabakMapper.findFacilities(lat, lng);
    }

    // 무거운 레이어(불가·주의 구역 합치기, 차박 후보지 점수)는 처음 한 번만 계산하고 기억해 둔다
    // 데이터는 거의 안 바뀐다 (DB 데이터를 바꾸면 서버를 다시 켜야 새로 계산)
    // 키: no · caution · open (camp 는 camp 표 그대로라 가벼워서 매번 읽음)
    private final Map<String, List<ChabakLayerItem>> layerCache = new ConcurrentHashMap<>();

    // 차박 지도 레이어 (판정 표시): no 불가 구역 · caution 주의 구역 · open 차박 후보지 · camp 등록 야영장
    public List<ChabakLayerItem> layer(String type) {
        return switch (type == null ? "" : type) {
            case "no" -> layerCache.computeIfAbsent(type, t -> toItems(single(chabakMapper.findNoZone())));
            case "caution" -> layerCache.computeIfAbsent(type, t -> toItems(single(chabakMapper.findCautionZone())));
            // 차박 후보지: 자리마다 안전점수를 붙인다 (화면의 "주변 안전" 필터용, 찍은 지점과 같은 계산)
            // 46곳 × 시설 계산이라 처음 한 번만 하고 기억 (데이터를 바꾸면 서버 재시작)
            case "open" -> layerCache.computeIfAbsent(type, t -> {
                List<ChabakLayerRow> spots = chabakMapper.findOpenSpots();
                spots.forEach(s -> {
                    List<ChabakScoreRow> parts = chabakMapper.findScoreParts(s.getLat(), s.getLng());
                    s.setScore(scoreOf(parts));
                    s.setErDriveMin(erDriveMinOf(parts)); // /score 의 erDriveMin 과 같은 어림
                });
                return toItems(spots);
            });
            // 등록 야영장: camp.safety_score 그대로 (카카오 차 시간 기준 — 후보지 점수와 기준이 달라 한 목록에서 비교할 때 주의)
            case "camp" -> toItems(chabakMapper.findCamps());
            default -> throw new InvalidRequestException("레이어 종류(type)는 no · caution · open · camp 중 하나입니다.");
        };
    }

    // 합친 구역이 비어 있으면(해당 구역 없음) 빈 목록
    private static List<ChabakLayerRow> single(ChabakLayerRow row) {
        return row == null || row.getGeojson() == null ? List.of() : List.of(row);
    }

    private static List<ChabakLayerItem> toItems(List<ChabakLayerRow> rows) {
        return rows.stream()
                .map(r -> new ChabakLayerItem(r.getId(), r.getName(), r.getLat(), r.getLng(),
                        r.getScore(), r.getScore() == null ? null : CampService.gradeOf(r.getScore()),
                        r.getArea(), r.getImageUrl(), r.getErDriveMin(),
                        r.getGeojson() == null ? null : JSON.readTree(r.getGeojson())))
                .toList();
    }

    private static void checkPosition(Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw new InvalidRequestException("위치(lat·lng)를 함께 보내 주세요.");
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new InvalidRequestException("위치 값이 올바르지 않습니다.");
        }
    }
}
