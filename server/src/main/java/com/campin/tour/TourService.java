package com.campin.tour;

import org.springframework.stereotype.Service;

import com.campin.common.InvalidRequestException;
import com.campin.place.PlaceMapper;

import lombok.RequiredArgsConstructor;

// 관광지 상세
@Service
@RequiredArgsConstructor
public class TourService {

    private final TourMapper tourMapper;
    private final PlaceMapper placeMapper;

    public TourDetailResponse detail(Long id, Long campId) {
        Tour t = tourMapper.findById(id);
        if (t == null) {
            throw new InvalidRequestException("없는 관광지입니다.");
        }
        return new TourDetailResponse(t.getId(), t.getName(), t.getCategoryLabel(), t.getPopularityRank(),
                t.getSido(), t.getSigungu(), t.getEupmyeon(), t.getLat(), t.getLng(), t.isDummy(),
                fromOf(id, campId),
                placeMapper.findInfos(PlaceMapper.TOUR, id),
                placeMapper.findNotes(PlaceMapper.TOUR, id));
    }

    private TourDetailResponse.From fromOf(Long id, Long campId) {
        TourFrom from = campId == null ? null : tourMapper.findFrom(id, campId);
        if (from == null) {
            return null;
        }
        return new TourDetailResponse.From(from.getCampId(), from.getCampName(), from.getLat(), from.getLng(),
                from.getDriveMin());
    }
}
