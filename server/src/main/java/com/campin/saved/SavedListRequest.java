package com.campin.saved;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 목록 만들기·이름 바꾸기 요청
public record SavedListRequest(
        @NotBlank(message = "목록 이름을 입력해 주세요.")
        @Size(max = 30, message = "목록 이름은 30자까지 입력할 수 있습니다.")
        String name
) {
}
