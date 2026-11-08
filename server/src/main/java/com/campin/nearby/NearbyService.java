package com.campin.nearby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.campin.camp.CampService;
import com.campin.common.DriveTone;
import com.campin.common.InvalidRequestException;

import lombok.RequiredArgsConstructor;

// 내 주변 탭: 내 위치 → 가장 가까운 캠핑장 기준 안전점수 · 가까운 시설
@Service
@RequiredArgsConstructor
public class NearbyService {

    // 기준 캠핑장이 이보다 멀면 "지금 이 위치" 점수라고 하기 어렵다 → 화면에 안내 (임시 기준)
    static final double FAR_KM = 10.0;

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final NearbyMapper nearbyMapper;

    public List<NearbyLayerResponse> layers() {
        return Arrays.stream(NearbyLayer.values()).map(NearbyLayerResponse::from).toList();
    }

    public NearbyResponse find(Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw new InvalidRequestException("위치(lat·lng)를 함께 보내 주세요.");
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new InvalidRequestException("위치 값이 올바르지 않습니다.");
        }

        NearbyCamp camp = nearbyMapper.findNearestCamp(lat, lng);
        if (camp == null) {
            return new NearbyResponse(true, null, null, "unknown", List.of());
        }

        double km = round1(distanceKm(lat, lng, camp.getLat(), camp.getLng()));
        NearbyResponse.Basis basis = new NearbyResponse.Basis(camp.getId(), camp.getName(), camp.getSido(),
                camp.getSigungu(), camp.getEupmyeon(), camp.getLat(), camp.getLng(), km, km > FAR_KM);

        List<NearbyResponse.Facility> facilities = new ArrayList<>();
        for (NearbyFacilityRow f : nearbyMapper.findFacilities(camp.getId())) {
            facilities.add(new NearbyResponse.Facility(facilities.size() + 1, f.getId(), f.getType(), f.getName(),
                    f.getLat(), f.getLng(), f.getDistanceKm(), f.getDriveMin(), DriveTone.of(f.getDriveMin()),
                    f.isDummy()));
        }
        boolean dummy = camp.isDummy() || facilities.stream().anyMatch(NearbyResponse.Facility::dummy);
        return new NearbyResponse(dummy, basis, camp.getSafetyScore(), CampService.gradeOf(camp.getSafetyScore()),
                facilities);
    }

    // 두 좌표 사이 직선거리(km) — 하버사인 공식 (지구를 공으로 보고 계산)
    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
