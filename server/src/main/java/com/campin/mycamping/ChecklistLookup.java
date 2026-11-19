package com.campin.mycamping;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.campin.alert.Alert;
import com.campin.alert.Sido;
import com.campin.alert.WarningSource;
import com.campin.camp.Camp;
import com.campin.camp.CampMapper;
import com.campin.camp.CampService;
import com.campin.chabak.ChabakScoreResponse;
import com.campin.chabak.ChabakService;
import com.campin.weather.DayForecast;
import com.campin.weather.ForecastSource;

import lombok.RequiredArgsConstructor;

// ChecklistSource 의 진짜 구현: camp 표 · 차박 안전점수 · 기상청 특보(10분 캐시된 WarningSource) · 기상청 단기예보(ForecastSource)
@Component
@RequiredArgsConstructor
public class ChecklistLookup implements ChecklistSource {

    private final CampMapper campMapper;
    private final ChabakService chabakService;
    private final WarningSource warningSource;
    private final ForecastSource forecastSource;

    @Override
    public Optional<Target> ofCamp(Long campId) {
        Camp c = campMapper.findById(campId);
        if (c == null) {
            return Optional.empty();
        }
        // 캠핑장 목록·상세와 같은 값 (안전점수 · 등급 · 응급실 차로 몇 분)
        return Optional.of(new Target(c.getLat(), c.getLng(),
                new ChecklistResponse.Safety(c.getSafetyScore(), CampService.gradeOf(c.getSafetyScore()),
                        c.getErDriveMin(), false)));
    }

    @Override
    public Target ofSpot(double lat, double lng) {
        ChabakScoreResponse s = chabakService.score(lat, lng);
        return new Target(lat, lng, new ChecklistResponse.Safety(s.score(), s.grade(), s.erDriveMin(), true));
    }

    @Override
    public Optional<List<ChecklistResponse.Warning>> warnings(double lat, double lng) {
        // 대한민국 근처가 아니면 지역을 정할 수 없다 (alert/AlertService 와 같은 범위)
        if (lat < 32 || lat > 39.5 || lng < 124 || lng > 132) {
            return Optional.empty();
        }
        String region = Sido.nearest(lat, lng); // 임시: 가장 가까운 시·도 중심 (카카오 좌표→행정구역 연결 때 교체)
        return warningSource.activeWarnings().map(all -> all.stream()
                .filter(Alert::active)
                .filter(a -> region.equals(a.sido()))
                .map(a -> new ChecklistResponse.Warning(a.category(), a.area(), a.message()))
                .toList());
    }

    @Override
    public Optional<List<DayForecast>> forecast(double lat, double lng) {
        if (lat < 32 || lat > 39.5 || lng < 124 || lng > 132) {
            return Optional.empty();
        }
        return forecastSource.daily(lat, lng);
    }
}
