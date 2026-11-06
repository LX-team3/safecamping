package com.campin.alert;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.campin.common.InvalidRequestException;

// 알림 (기상특보 + 재난문자 모아 보기). 지금은 더미(AlertDummyData)
@Service
public class AlertService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // sido 를 주면 그 지역, 위치(lat·lon)를 주면 가장 가까운 시·도, 둘 다 없으면 전국
    public AlertResponse find(String sido, Double lat, Double lon) {
        String region = resolveRegion(sido, lat, lon);

        List<Alert> items = AlertDummyData.all().stream()
                .filter(a -> region == null || a.sido().equals(region))
                .sorted(Comparator.comparing(Alert::issuedAt).reversed())  // 최신순
                .toList();
        List<Alert> active = items.stream().filter(a -> "warning".equals(a.kind()) && a.active()).toList();
        LocalDate today = LocalDate.now(KST);
        int todayDisasters = (int) items.stream()
                .filter(a -> "disaster".equals(a.kind()))
                .filter(a -> a.issuedAt().atZoneSameInstant(KST).toLocalDate().equals(today))
                .count();

        return new AlertResponse(true, region, Sido.labels(), active, todayDisasters, items);
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
