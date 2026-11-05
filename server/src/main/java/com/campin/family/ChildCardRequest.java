package com.campin.family;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

// 아이 카드 추가·수정 요청. 이름 말고는 모두 선택 (모르는 칸은 비워 둔다)
public record ChildCardRequest(
        @NotBlank(message = "아이 이름을 입력해 주세요.")
        @Size(max = 20, message = "이름은 20자까지 입력할 수 있습니다.")
        String name,

        @PastOrPresent(message = "생년월일이 올바르지 않습니다.")  // 미래 날짜는 400
        LocalDate birthDate,

        @Size(max = 10, message = "혈액형이 올바르지 않습니다.")
        String bloodType,

        @Size(max = 200, message = "알레르기는 200자까지 입력할 수 있습니다.")
        String allergy,

        @Size(max = 200, message = "지병은 200자까지 입력할 수 있습니다.")
        String disease,

        @Size(max = 200, message = "복용 약은 200자까지 입력할 수 있습니다.")
        String medicine,

        @Size(max = 300, message = "메모는 300자까지 입력할 수 있습니다.")
        String memo
) {
}
