package com.campin.member;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// member_activity 테이블 SQL. 테이블 정의는 db/schema.sql
@Mapper
public interface ActivityMapper {

    @Select("SELECT activity FROM member_activity WHERE member_id = #{memberId}")
    List<String> findByMemberId(@Param("memberId") Long memberId);

    // 저장할 때는 통째로 바꾼다: 모두 지우고 → 고른 것만 다시 넣는다
    @Delete("DELETE FROM member_activity WHERE member_id = #{memberId}")
    void deleteByMemberId(@Param("memberId") Long memberId);

    @Insert("INSERT INTO member_activity (member_id, activity) VALUES (#{memberId}, #{activity})")
    void insert(@Param("memberId") Long memberId, @Param("activity") String activity);
}
