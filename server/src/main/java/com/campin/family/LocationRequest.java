package com.campin.family;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

// 내 위치 저장 요청 (위치 공유에 동의했을 때 브라우저 GPS 값)
// 이름은 조회 응답(LocationResponse)과 같게 lat · lng 로 받는다. DB 에 넣을 때 lng → lon 으로 바꾼다
public record LocationRequest(
        // 위도 범위 -90~90, 경도 범위 -180~180 을 벗어나면 400 (모일 장소 요청과 같은 규칙)
        @NotNull(message = "위치 값이 없습니다.")
        @DecimalMin(value = "-90", message = "위치 값이 올바르지 않습니다.")
        @DecimalMax(value = "90", message = "위치 값이 올바르지 않습니다.")
        Double lat,

        @NotNull(message = "위치 값이 없습니다.")
        @DecimalMin(value = "-180", message = "위치 값이 올바르지 않습니다.")
        @DecimalMax(value = "180", message = "위치 값이 올바르지 않습니다.")
        Double lng
) {
}
