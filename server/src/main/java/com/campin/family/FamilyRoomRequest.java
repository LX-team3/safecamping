package com.campin.family;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 가족방 만들기 요청
public record FamilyRoomRequest(
        @NotBlank(message = "가족방 이름을 입력해 주세요.")
        @Size(max = 20, message = "가족방 이름은 20자까지 입력할 수 있습니다.")
        String name
) {
}
