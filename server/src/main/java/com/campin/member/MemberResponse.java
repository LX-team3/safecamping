package com.campin.member;


// 화면에 돌려줄 회원 정보. 비밀번호(암호화 값 포함)는 절대 담지 않는다
public record MemberResponse(Long id, String loginId, String name) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getLoginId(), member.getName());
    }
}
