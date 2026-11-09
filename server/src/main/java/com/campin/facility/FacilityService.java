package com.campin.facility;

import org.springframework.stereotype.Service;

import com.campin.camp.CampService;
import com.campin.common.DriveTone;
import com.campin.common.InvalidRequestException;
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
