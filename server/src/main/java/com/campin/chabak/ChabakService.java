package com.campin.chabak;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

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
        return JSON.readValue(json, ChabakVerdictResponse.class);
    }

    // 찍은 지점에서 종류별로 가까운 안전 시설 (응급실·소방서·지구대·편의점·약국 각 3곳)
    public List<ChabakFacility> facilities(Double lat, Double lng) {
        checkPosition(lat, lng);
        return chabakMapper.findFacilities(lat, lng);
    }

    // 구역 레이어(불가·주의)는 합치는 계산이 무거워서 처음 한 번만 하고 기억해 둔다
    // 규제 데이터는 거의 안 바뀐다 (바꾸면 서버를 다시 켜면 새로 계산)
    private final Map<String, List<ChabakLayerItem>> zoneCache = new ConcurrentHashMap<>();

    // 차박 지도 레이어 (판정 표시): no 불가 구역 · caution 주의 구역 · open 차박 후보지 · camp 등록 야영장
    public List<ChabakLayerItem> layer(String type) {
        return switch (type == null ? "" : type) {
            case "no" -> zoneCache.computeIfAbsent(type, t -> toItems(single(chabakMapper.findNoZone())));
            case "caution" -> zoneCache.computeIfAbsent(type, t -> toItems(single(chabakMapper.findCautionZone())));
            case "open" -> toItems(chabakMapper.findOpenSpots());
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
