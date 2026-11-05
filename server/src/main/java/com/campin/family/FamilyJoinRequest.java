package com.campin.family;

import jakarta.validation.constraints.NotBlank;

// 초대코드로 들어가기 요청. 소문자·공백은 서비스에서 정리하므로 여기서는 비었는지만 본다
public record FamilyJoinRequest(
        @NotBlank(message = "초대코드를 입력해 주세요.")
        String inviteCode
) {
}
