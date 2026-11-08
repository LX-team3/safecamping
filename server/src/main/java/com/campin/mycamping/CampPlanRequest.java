package com.campin.mycamping;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 내 캠핑 등록 요청. 날짜는 "2026-11-14" 형식 (시간 없음)
// 날짜 선후·지난 날짜·중복 검사는 서비스에서 한다 (여기서는 값이 있는지·길이만)
public record CampPlanRequest(
        @NotNull(message = "캠핑장을 골라 주세요.")
        Long campId,

        @NotBlank(message = "캠핑장 이름이 없습니다.")
        @Size(max = 100, message = "캠핑장 이름이 너무 깁니다.")
        String campName,

        @Size(max = 100, message = "지역 이름이 너무 깁니다.")
        String place,

        @NotNull(message = "체크인 날짜를 골라 주세요.")
        LocalDate checkIn,

        @NotNull(message = "체크아웃 날짜를 골라 주세요.")
        LocalDate checkOut,

        @Size(max = 100, message = "메모는 100자까지 입력할 수 있습니다.")
        String memo
) {
}
