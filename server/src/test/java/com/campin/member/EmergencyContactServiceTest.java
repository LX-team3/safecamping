package com.campin.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;

// DB 없이 비상연락처 규칙만 확인한다 (회원 한 명 + 메모리 비상연락처 표)
class EmergencyContactServiceTest {

    private final FakeEmergencyContactMapper contacts = new FakeEmergencyContactMapper();
    private EmergencyContactService service;

    @BeforeEach
    void setUp() {
        Member member = new Member();
        member.setId(1L);
        member.setLoginId("camper01");
        contacts.insert(1L, new EmergencyContact(null, "01011112222"));  // 회원가입 때 번호만 넣은 상태

        MemberMapper fakeMapper = new MemberMapper() {
            @Override public Member findByLoginId(String loginId) { return "camper01".equals(loginId) ? member : null; }
            @Override public boolean existsByLoginId(String loginId) { return false; }
            @Override public void insert(Member m) { }
        };
        service = new EmergencyContactService(fakeMapper, contacts);
    }

    private static EmergencyContactRequest req(EmergencyContact... list) {
        return new EmergencyContactRequest(new ArrayList<>(Arrays.asList(list)));
    }

    private static EmergencyContact c(String name, String phone) {
        return new EmergencyContact(name, phone);
    }

    @Test
    void 회원가입_때_넣은_번호가_이름_없이_보인다() {
        List<EmergencyContact> got = service.get("camper01");
        assertEquals(1, got.size());
        assertNull(got.get(0).getName());
        assertEquals("01011112222", got.get(0).getPhone());
    }

    @Test
    void 저장하면_목록을_통째로_바꾸고_번호는_숫자만_남는다() {
        service.update("camper01", req(c(" 엄마 ", "010-1234-5678"), c("아빠", "010 9876 5432")));
        List<EmergencyContact> got = service.get("camper01");
        assertEquals(2, got.size());
        assertEquals("엄마", got.get(0).getName());
        assertEquals("01012345678", got.get(0).getPhone());
        assertEquals("01098765432", got.get(1).getPhone());
    }

    @Test
    void 다섯_명까지_저장되고_빈_칸은_건너뛴다() {
        service.update("camper01", req(c("1", "01011111111"), c("", ""), c("2", "01022222222"),
                c("3", "01033333333"), c("4", "01044444444"), c("5", "01055555555")));
        assertEquals(5, service.get("camper01").size());
    }

    @Test
    void 여섯_명은_저장되지_않는다() {
        assertThrows(InvalidRequestException.class, () -> service.update("camper01", req(
                c("1", "01011111111"), c("2", "01022222222"), c("3", "01033333333"),
                c("4", "01044444444"), c("5", "01055555555"), c("6", "01066666666"))));
        assertEquals(1, service.get("camper01").size());  // 원래 목록 그대로
    }

    @Test
    void 이름이나_번호가_틀리면_저장되지_않는다() {
        assertThrows(InvalidRequestException.class, () -> service.update("camper01", req(c("", "01012345678"))));
        assertThrows(InvalidRequestException.class, () -> service.update("camper01", req(c("엄마", ""))));
        assertThrows(InvalidRequestException.class, () -> service.update("camper01", req(c("엄마", "1234"))));
    }

    @Test
    void 빈_목록을_보내면_모두_지운다() {
        service.update("camper01", req());
        assertTrue(service.get("camper01").isEmpty());
    }
}
