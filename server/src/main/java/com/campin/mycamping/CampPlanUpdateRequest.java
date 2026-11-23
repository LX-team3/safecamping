package com.campin.mycamping;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 내 캠핑 고치기 요청 (예약 정보 확인 화면에서 날짜 · 인원 · 동행 · 이동 수단을 고칠 때)
// 캠핑장(자리)은 바꾸지 않는다 → 다른 캠핑장이면 지우고 새로 등록한다
// 인원 · 동행 · 이동 수단은 일정에 칸이 없어서 memo 에 글자로 넣는다 (예: "성인 2명 · 어린이 0명 · 친구·연인 · 자동차")
public record CampPlanUpdateRequest(
        @NotNull(message = "체크인 날짜를 골라 주세요.")
        LocalDate checkIn,

        @NotNull(message = "체크아웃 날짜를 골라 주세요.")
        LocalDate checkOut,

        @Size(max = 100, message = "메모는 100자까지 입력할 수 있습니다.")
        String memo
) {
}
