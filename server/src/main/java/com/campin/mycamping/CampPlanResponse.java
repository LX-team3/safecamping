package com.campin.mycamping;

import java.time.LocalDate;

// 내 캠핑 카드 하나. 날짜는 "2026-11-14" 문자열로 나간다
public record CampPlanResponse(Long id, Long campId, String campName, String place,
                               LocalDate checkIn, LocalDate checkOut, String memo) {

    public static CampPlanResponse from(CampPlan p) {
        return new CampPlanResponse(p.getId(), p.getCampId(), p.getCampName(), p.getPlace(),
                p.getCheckIn(), p.getCheckOut(), p.getMemo());
    }
}
