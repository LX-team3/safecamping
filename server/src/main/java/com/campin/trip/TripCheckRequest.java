package com.campin.trip;

import java.time.LocalDate;
import java.util.List;

// POST /api/trips/check 요청 (누구와, 언제 시트)
// 날짜는 "2026-11-14" 형식 → 스프링(Jackson)이 LocalDate 로 바꿔 준다
// childAges: 아이 수만큼 나이대 (infant 영유아 0~3세 / preschool 미취학 4~7세 / elementary 초등 8~12세)
public record TripCheckRequest(
        LocalDate checkIn,
        LocalDate checkOut,
        Integer adults,
        Integer kids,
        List<String> childAges) {
}
