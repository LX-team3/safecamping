package com.campin.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 회원가입 요청. 값 형식 검사는 여기 어노테이션으로, 비밀번호 확인·중복 같은 규칙은 SignupService 에서 한다
public record SignupRequest(
        // @NotBlank: null·빈 문자열·공백만 있는 값을 막는다
        @NotBlank(message = "아이디를 입력해 주세요.")
        // @Pattern: 정규식에 맞지 않으면 message 를 돌려준다
        @Pattern(regexp = "^[A-Za-z0-9]{4,16}$", message = "아이디는 영문·숫자 4~16자입니다.")
        String loginId,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        // @Size: 길이 제한. 72바이트를 넘으면 BCrypt 가 뒤를 잘라 버리므로 막는다
        @Size(min = 8, max = 72, message = "비밀번호는 8~72자입니다.")
        String password,

        @NotBlank(message = "비밀번호를 한 번 더 입력해 주세요.")
        String passwordConfirm,

        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 20, message = "이름은 20자까지 입력할 수 있습니다.")
        String name,

        @NotBlank(message = "연락처를 입력해 주세요.")
        String phone,

        @NotBlank(message = "비상연락처를 입력해 주세요.")
        String emergencyPhone,

        @NotBlank(message = "주소를 입력해 주세요.")
        @Size(max = 200, message = "주소가 너무 깁니다.")
        String address,

        @Size(max = 100, message = "상세 주소가 너무 깁니다.")
        String addressDetail
) {
}
