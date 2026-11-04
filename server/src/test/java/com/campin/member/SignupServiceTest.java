package com.campin.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.campin.common.InvalidRequestException;

// DB 없이 회원가입 규칙만 확인한다 (mapper 는 메모리 Map 으로 대신함)
class SignupServiceTest {

    private final Map<String, Member> table = new HashMap<>();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private SignupService service;

    @BeforeEach
    void setUp() {
        MemberMapper fakeMapper = new MemberMapper() {
            @Override public Member findByLoginId(String loginId) { return table.get(loginId); }
            @Override public boolean existsByLoginId(String loginId) { return table.containsKey(loginId); }
            @Override public void insert(Member member) {
                member.setId((long) table.size() + 1);
                table.put(member.getLoginId(), member);
            }
        };
        service = new SignupService(fakeMapper, encoder);
    }

    private SignupRequest request(String loginId, String pw, String pw2, String phone) {
        return new SignupRequest(loginId, pw, pw2, "홍길동", phone, "010 9876 5432", "서울시 어딘가", "");
    }

    @Test
    void 가입하면_비밀번호는_암호화되고_번호는_숫자만_저장된다() {
        service.signup(request("camper01", "password1", "password1", "010-1234-5678"));

        Member saved = table.get("camper01");
        assertNotEquals("password1", saved.getPasswordHash());
        assertTrue(encoder.matches("password1", saved.getPasswordHash()));
        assertEquals("01012345678", saved.getPhone());
        assertEquals("01098765432", saved.getEmergencyPhone());
    }

    @Test
    void 비밀번호_확인이_다르면_가입되지_않는다() {
        assertThrows(InvalidRequestException.class,
                () -> service.signup(request("camper01", "password1", "password2", "01012345678")));
    }

    @Test
    void 같은_아이디로는_두_번_가입되지_않는다() {
        service.signup(request("camper01", "password1", "password1", "01012345678"));
        assertThrows(InvalidRequestException.class,
                () -> service.signup(request("camper01", "password1", "password1", "01012345678")));
        assertEquals(false, service.isLoginIdAvailable("camper01"));
    }

    @Test
    void 연락처_형식이_틀리면_가입되지_않는다() {
        assertThrows(InvalidRequestException.class,
                () -> service.signup(request("camper01", "password1", "password1", "1234")));
    }
}
