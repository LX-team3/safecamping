package com.campin.member;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;



import lombok.RequiredArgsConstructor;

// 스프링 시큐리티가 로그인할 때 "이 아이디의 회원 정보(암호화된 비밀번호 포함)"를 가져오는 곳
@Service
@RequiredArgsConstructor
public class MemberDetailsService implements UserDetailsService {

    private final MemberMapper memberMapper;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        Member member = memberMapper.findByLoginId(loginId);
        if (member == null) {
            // 시큐리티가 이 예외를 비밀번호 틀림과 똑같이 처리한다 → 아이디가 있는지 없는지 밖에서 알 수 없다
            throw new UsernameNotFoundException(loginId);
        }
        // 비밀번호 비교는 시큐리티가 PasswordEncoder(BCrypt)로 직접 한다
        return User.withUsername(member.getLoginId())
                .password(member.getPasswordHash())
                .roles("USER")
                .build();
    }
}
