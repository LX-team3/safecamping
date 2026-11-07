package com.campin.trip;

import java.time.LocalDate;
import java.util.List;

// 검사를 통과한 캠핑 조건 + 몇 박인지
public record TripCheckResponse(
        LocalDate checkIn,
        LocalDate checkOut,
        long nights,
        int adults,
        int kids,
        List<String> childAges) {
}
