package com.campin.member;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campin.common.InvalidRequestException;

import lombok.RequiredArgsConstructor;

@Service                   // 스프링: 비즈니스 로직 담당 빈으로 등록
@RequiredArgsConstructor   // Lombok: final 필드를 받는 생성자를 만든다 → 스프링이 그 생성자로 주입
public class SignupService {

    private static final String LOGIN_ID_PATTERN = "^[A-Za-z0-9]{4,16}$";
    // 휴대폰 번호: 01 로 시작하는 10~11자리
    private static final String PHONE_PATTERN = "^01\\d{8,9}$";

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    public boolean isLoginIdAvailable(String loginId) {
        if (loginId == null || !loginId.matches(LOGIN_ID_PATTERN)) {
            throw new InvalidRequestException("아이디는 영문·숫자 4~16자입니다.");
        }
        return !memberMapper.existsByLoginId(loginId);
    }

    @Transactional  // 스프링: 메서드 안의 DB 작업을 하나로 묶는다 (중간에 실패하면 되돌림)
    public MemberResponse signup(SignupRequest req) {
        if (!req.password().equals(req.passwordConfirm())) {
            throw new InvalidRequestException("비밀번호가 서로 다릅니다.");
        }
        String phone = toDigits(req.phone());
        String emergencyPhone = toDigits(req.emergencyPhone());
        if (!phone.matches(PHONE_PATTERN)) {
            throw new InvalidRequestException("연락처를 다시 확인해 주세요.");
        }
        if (!emergencyPhone.matches(PHONE_PATTERN)) {
            throw new InvalidRequestException("비상연락처를 다시 확인해 주세요.");
        }
        // 화면에서 중복 확인을 했어도, 그사이 누가 가입했을 수 있으므로 한 번 더 본다
        if (memberMapper.existsByLoginId(req.loginId())) {
            throw new InvalidRequestException("이미 사용 중인 아이디입니다.");
        }

        Member member = new Member();
        member.setLoginId(req.loginId());
        member.setPasswordHash(passwordEncoder.encode(req.password()));
        member.setName(req.name().trim());
        member.setPhone(phone);
        member.setEmergencyPhone(emergencyPhone);
        member.setAddress(req.address().trim());
        member.setAddressDetail(req.addressDetail() == null || req.addressDetail().isBlank()
                ? null : req.addressDetail().trim());

        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            // 위 검사와 INSERT 사이에 같은 아이디가 먼저 들어온 경우 (login_id UNIQUE 제약)
            throw new InvalidRequestException("이미 사용 중인 아이디입니다.");
        }
        return MemberResponse.from(member);
    }

    // "010 1234 5678", "010-1234-5678" 처럼 입력해도 숫자만 남겨 저장한다
    private static String toDigits(String value) {
        return value.replaceAll("\\D", "");
    }
}
