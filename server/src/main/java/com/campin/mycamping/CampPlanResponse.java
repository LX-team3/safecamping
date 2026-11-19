package com.campin.mycamping;

import java.time.LocalDate;

// 내 캠핑 카드 하나. 날짜는 "2026-11-14" 문자열로 나간다
// campId 가 null 이면 캠핑장이 아닌 자리(차박) — 화면은 lat·lng 로 차박 지도에서 보여 준다
public record CampPlanResponse(Long id, Long campId, String campName, String place, Double lat, Double lng,
                               LocalDate checkIn, LocalDate checkOut, String memo) {

    public static CampPlanResponse from(CampPlan p) {
        return new CampPlanResponse(p.getId(), p.getCampId(), p.getCampName(), p.getPlace(),
                p.getLat(), p.getLng(), p.getCheckIn(), p.getCheckOut(), p.getMemo());
    }
}
