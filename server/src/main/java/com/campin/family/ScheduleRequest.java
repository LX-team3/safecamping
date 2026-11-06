package com.campin.family;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 일정 추가 요청. startAt 은 시간대가 붙은 형식 (예: "2026-11-14T06:00:00+09:00" 또는 "...Z")
public record ScheduleRequest(
        @NotBlank(message = "일정 내용을 입력해 주세요.")
        @Size(max = 50, message = "일정 내용은 50자까지 입력할 수 있습니다.")
        String title,

        @NotNull(message = "날짜와 시간을 골라 주세요.")
        OffsetDateTime startAt,

        @Size(max = 200, message = "메모는 200자까지 입력할 수 있습니다.")
        String memo
) {
}
