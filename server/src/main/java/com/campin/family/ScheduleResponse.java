package com.campin.family;

import java.time.OffsetDateTime;

// 일정 하나. 시간은 화면에서 한국 시간으로 바꿔 보여 준다
public record ScheduleResponse(Long id, String title, OffsetDateTime startAt, String memo, String createdByName) {

    public static ScheduleResponse from(FamilySchedule s) {
        return new ScheduleResponse(s.getId(), s.getTitle(), s.getStartAt(), s.getMemo(), s.getCreatedByName());
    }
}
