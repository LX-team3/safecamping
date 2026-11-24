package com.campin.disaster;

import java.time.LocalDateTime;

// 상류 수위관측소의 지금 수위와 공식 기준수위 (한강홍수통제소). 단위는 모두 m (수위표 눈금, 해발 아님)
// attention 관심 · alarm 경보 · serious 심각 · planned 계획홍수위. 관측소에 그 기준이 없으면 null
public record WaterLevel(String station, LocalDateTime observedAt, Double wl,
                         Double attention, Double alarm, Double serious, Double planned) {
}
