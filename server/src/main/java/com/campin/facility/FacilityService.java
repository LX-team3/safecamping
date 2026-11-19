package com.campin.facility;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.campin.camp.CampService;
import com.campin.common.DriveTone;
import com.campin.common.InvalidRequestException;
import com.campin.nearby.NearbyLayer;
import com.campin.place.PlaceMapper;

import lombok.RequiredArgsConstructor;

// 시설 상세
@Service
@RequiredArgsConstructor
public class FacilityService {

    private final FacilityMapper facilityMapper;
    private final PlaceMapper placeMapper;

    public FacilityDetailResponse detail(Long id, Long campId) {
        Facility f = facilityMapper.findById(id);
        if (f == null) {
            throw new InvalidRequestException("없는 시설입니다.");
        }
        return new FacilityDetailResponse(f.getId(), f.getType(), f.getName(), f.getSido(), f.getSigungu(),
                f.getEupmyeon(), f.getAddress(), f.getPhone(), f.getHours(), f.getLat(), f.getLng(), f.isDummy(),
                fromOf(id, campId),
                placeMapper.findInfos(PlaceMapper.FACILITY, id),
                placeMapper.findNotes(PlaceMapper.FACILITY, id));
    }

    // 지도에 그릴 수 있는 종류 = 지도 설정 시트의 줄 (NearbyLayer) 중 데이터가 있는 것
    private static final Set<String> MAP_TYPES = Arrays.stream(NearbyLayer.values())
            .filter(NearbyLayer::ready).map(NearbyLayer::code).collect(Collectors.toSet());
    static final int MAP_LIMIT = 300; // 한 번에 보내는 점 수 상한 (넓게 보면 수천 곳이라 지도가 느려짐)

    // 지도 화면 안의 시설 (탐색 지도 레이어)
    public List<FacilityPin> inBounds(String types, Double swLat, Double swLng, Double neLat, Double neLng) {
        if (swLat == null || swLng == null || neLat == null || neLng == null) {
            throw new InvalidRequestException("지도 범위(swLat·swLng·neLat·neLng)를 함께 보내 주세요.");
        }
        if (swLat > neLat || swLng > neLng) {
            throw new InvalidRequestException("지도 범위 값이 올바르지 않습니다.");
        }
        List<String> picked = types == null ? List.of()
                : Arrays.stream(types.split(",")).map(String::trim).filter(MAP_TYPES::contains).distinct().toList();
        if (picked.isEmpty()) {
            return List.of(); // 켠 종류가 없으면 DB 를 부르지 않는다
        }
        return facilityMapper.findInBounds(picked, swLat, swLng, neLat, neLng, MAP_LIMIT);
    }

    private FacilityDetailResponse.From fromOf(Long id, Long campId) {
        FacilityFrom from = campId == null ? null : facilityMapper.findFrom(id, campId);
        if (from == null) {
            return null;
        }
        return new FacilityDetailResponse.From(from.getCampId(), from.getCampName(), from.getLat(), from.getLng(),
                from.getDistanceKm(), from.getDriveMin(), DriveTone.of(from.getDriveMin()),
                CampService.gradeOf(from.getSafetyScore()));
    }
}
