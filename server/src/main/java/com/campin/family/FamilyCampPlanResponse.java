package com.campin.family;

import java.time.LocalDate;

// 가족 캠핑 카드 하나. 내 캠핑 카드(CampPlanResponse)와 같은 칸 + 등록한 사람
public record FamilyCampPlanResponse(Long id, Long ownerId, String ownerName, Long campId, String campName,
                                     String place, Double lat, Double lng,
                                     LocalDate checkIn, LocalDate checkOut, String memo) {

    public static FamilyCampPlanResponse from(FamilyCampPlan p) {
        return new FamilyCampPlanResponse(p.getId(), p.getOwnerId(), p.getOwnerName(), p.getCampId(), p.getCampName(),
                p.getPlace(), p.getLat(), p.getLng(), p.getCheckIn(), p.getCheckOut(), p.getMemo());
    }
}
