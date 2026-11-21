package com.campin.disaster;

import java.time.LocalDateTime;

// 한 시간 동안 내린 비 (기상청 초단기실황 RN1)
// at: 관측 시각(정시, 한국 시간). at 이 15:00 이면 14:00~15:00 사이에 내린 비
// mm: 내린 비(mm). 기상청이 값을 주지 않았거나 결측이면 null (0 으로 채우지 않는다)
public record HourRain(LocalDateTime at, Double mm) {
}
