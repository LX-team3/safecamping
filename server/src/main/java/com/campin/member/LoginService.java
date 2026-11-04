package com.campin.member;

import org.springframework.stereotype.Service;


import lombok.RequiredArgsConstructor;

// 로그인한 회원 정보를 화면용으로 꺼낸다 (아이디·비밀번호 확인은 스프링 시큐리티가 MemberDetailsService 로 한다)
@Service
@RequiredArgsConstructor
public class LoginService {

    private final MemberMapper memberMapper;

    public MemberResponse findMember(String loginId) {
        Member member = memberMapper.findByLoginId(loginId);
        return member == null ? null : MemberResponse.from(member);
    }
}
