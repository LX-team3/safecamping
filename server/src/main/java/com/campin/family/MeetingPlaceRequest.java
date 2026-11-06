package com.campin.family;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 모일 장소 추가 요청. 좌표는 선택이지만, 넣을 때는 경도·위도를 둘 다 넣는다 (서비스에서 확인)
public record MeetingPlaceRequest(
        @NotBlank(message = "장소 이름을 입력해 주세요.")
        @Size(max = 30, message = "장소 이름은 30자까지 입력할 수 있습니다.")
        String name,

        @Size(max = 200, message = "설명은 200자까지 입력할 수 있습니다.")
        String description,

        // 경도 범위 -180~180, 위도 범위 -90~90 을 벗어나면 400
        @DecimalMin(value = "-180", message = "위치 값이 올바르지 않습니다.")
        @DecimalMax(value = "180", message = "위치 값이 올바르지 않습니다.")
        Double lon,

        @DecimalMin(value = "-90", message = "위치 값이 올바르지 않습니다.")
        @DecimalMax(value = "90", message = "위치 값이 올바르지 않습니다.")
        Double lat
) {
}
