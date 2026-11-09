package com.campin.member;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// member_child 테이블 SQL (회원의 함께 가는 아이 나이대). 테이블 정의는 db/schema_02.sql
// 로그인 아이디로 member.id 를 SQL 안에서 찾는다
@Mapper
public interface MemberChildMapper {

    @Select("""
            SELECT c.age_group
              FROM member_child c
              JOIN member m ON m.id = c.member_id
             WHERE m.login_id = #{loginId}
             ORDER BY c.sort_order
            """)
    List<String> findAgeGroups(@Param("loginId") String loginId);

    // 이 회원의 아이 줄만 지운다 (저장할 때 지우고 새로 넣기)
    @Delete("""
            DELETE FROM member_child
             WHERE member_id = (SELECT id FROM member WHERE login_id = #{loginId})
            """)
    void deleteByLoginId(@Param("loginId") String loginId);

    @Insert("""
            INSERT INTO member_child (member_id, sort_order, age_group)
            SELECT id, #{sortOrder}, #{ageGroup} FROM member WHERE login_id = #{loginId}
            """)
    void insert(@Param("loginId") String loginId, @Param("sortOrder") int sortOrder,
                @Param("ageGroup") String ageGroup);
}
