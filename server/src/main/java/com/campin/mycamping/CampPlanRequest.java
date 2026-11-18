package com.campin.mycamping;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 내 캠핑 등록 요청. 날짜는 "2026-11-14" 형식 (시간 없음)
// 두 가지 중 하나: 등록 캠핑장(campId) 또는 캠핑장이 아닌 자리(차박 후보지 · 지도에서 찍은 자리 = lat·lng)
// 둘 중 어느 것도 없는지, 날짜 선후·지난 날짜·중복 검사는 서비스에서 한다 (여기서는 값이 있는지·길이만)
public record CampPlanRequest(
        Long campId,           // 등록 캠핑장이면 camp.id, 자리면 null

        @NotBlank(message = "이름이 없습니다.")
        @Size(max = 100, message = "이름이 너무 깁니다.")
        String campName,

        @Size(max = 100, message = "지역 이름이 너무 깁니다.")
        String place,

        Double lat,            // 자리(campId 가 없을 때)의 위도·경도
        Double lng,

        @NotNull(message = "체크인 날짜를 골라 주세요.")
        LocalDate checkIn,

        @NotNull(message = "체크아웃 날짜를 골라 주세요.")
        LocalDate checkOut,

        @Size(max = 100, message = "메모는 100자까지 입력할 수 있습니다.")
        String memo
) {
}
