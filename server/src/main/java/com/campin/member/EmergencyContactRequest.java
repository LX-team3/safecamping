package com.campin.member;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 비상연락처 저장 요청. 화면에 있는 사람 전부를 순서대로 보낸다 (빈 목록이면 모두 지움)
public record EmergencyContactRequest(
        @NotNull(message = "비상연락처를 보내 주세요.")
        @Size(max = EmergencyContactService.MAX, message = "비상연락처는 5명까지 넣을 수 있습니다.")
        List<EmergencyContact> contacts
) {
}
