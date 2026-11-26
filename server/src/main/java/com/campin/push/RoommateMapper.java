package com.campin.push;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// 브리핑 푸시를 함께 받을 모임방 사람 찾기 (읽기만 한다). 테이블 정의는 db/schema.sql 의 family_member
// FamilyMapper 에 넣지 않은 이유: 폰 주소록(PushTokenStore)이 회원 번호가 아니라 로그인 아이디로 되어 있어 푸시에서만 쓴다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface RoommateMapper {

    // 같은 방 사람들의 로그인 아이디 (나는 빼고, 먼저 들어온 순서). 방이 없으면 빈 목록
    @Select("""
            SELECT m.login_id
              FROM member mine
              JOIN family_member me ON me.member_id = mine.id
              JOIN family_member other ON other.family_room_id = me.family_room_id
                                      AND other.member_id <> me.member_id
              JOIN member m ON m.id = other.member_id
             WHERE mine.login_id = #{loginId}
             ORDER BY other.joined_at, other.id
            """)
    List<String> findRoommates(@Param("loginId") String loginId);
}
