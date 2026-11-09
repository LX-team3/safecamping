package com.campin.alert;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.campin.common.InvalidRequestException;

import lombok.RequiredArgsConstructor;

// 알림 (기상특보 + 재난문자 모아 보기)
// 기상특보: 기상청 실제 데이터(WarningSource). 받지 못하면 더미로 대신하고 warningDummy=true
// 재난문자: 행정안전부 긴급재난문자(DisasterSource). 받지 못하면 더미로 대신하고 disasterDummy=true
@Service
@RequiredArgsConstructor
public class AlertService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WarningSource warningSource;
    private final DisasterSource disasterSource;

    // sido 를 주면 그 지역, 위치(lat·lon)를 주면 가장 가까운 시·도, 둘 다 없으면 전국
    public AlertResponse find(String sido, Double lat, Double lon) {
        String region = resolveRegion(sido, lat, lon);

        Optional<List<Alert>> realWarnings = warningSource.activeWarnings();
        boolean warningDummy = realWarnings.isEmpty();
        List<Alert> warnings = realWarnings.orElseGet(AlertDummyData::warnings);
        Optional<List<Alert>> realDisasters = disasterSource.recentMessages();
        boolean disasterDummy = realDisasters.isEmpty();
        List<Alert> disasters = realDisasters.orElseGet(AlertDummyData::disasters);

        List<Alert> items = Stream.concat(warnings.stream(), disasters.stream())
                // 전국에 보낸 재난문자는 어느 지역을 골라도 보인다
                .filter(a -> region == null || a.sido().equals(region)
                        || DisasterMessageParser.NATIONWIDE.equals(a.sido()))
                .sorted(Comparator.comparing(Alert::issuedAt).reversed())  // 최신순
                .toList();
        List<Alert> active = items.stream().filter(a -> "warning".equals(a.kind()) && a.active()).toList();
        LocalDate today = LocalDate.now(KST);
        int todayDisasters = (int) items.stream()
                .filter(a -> "disaster".equals(a.kind()))
                .filter(a -> a.issuedAt().atZoneSameInstant(KST).toLocalDate().equals(today))
                .count();

        return new AlertResponse(warningDummy, disasterDummy, region, Sido.labels(), active, todayDisasters, items);
    }

    private static String resolveRegion(String sido, Double lat, Double lon) {
        if (sido != null && !sido.isBlank()) {
            if (!Sido.isValid(sido)) {
                throw new InvalidRequestException("없는 지역입니다.");
            }
            return sido;
        }
        if (lat != null && lon != null) {
            // 대한민국 근처가 아니면(해외·잘못된 값) 지역을 정할 수 없다
            if (lat < 32 || lat > 39.5 || lon < 124 || lon > 132) {
                throw new InvalidRequestException("지금 위치로 지역을 찾을 수 없습니다. 지역을 직접 골라 주세요.");
            }
            return Sido.nearest(lat, lon);
        }
        return null;
    }
}
