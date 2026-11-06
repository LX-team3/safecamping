package com.campin.member;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// emergency_contact 테이블 SQL. 테이블 정의는 db/schema.sql
@Mapper
public interface EmergencyContactMapper {

    // 넣은 순서대로 (id 가 작을수록 먼저 넣은 것)
    @Select("SELECT name, phone FROM emergency_contact WHERE member_id = #{memberId} ORDER BY id")
    List<EmergencyContact> findByMemberId(@Param("memberId") Long memberId);

    // 내 정보에서 저장할 때는 목록을 통째로 바꾼다: 모두 지우고 → 화면 순서대로 다시 넣는다
    @Delete("DELETE FROM emergency_contact WHERE member_id = #{memberId}")
    void deleteByMemberId(@Param("memberId") Long memberId);

    @Insert("INSERT INTO emergency_contact (member_id, name, phone) VALUES (#{memberId}, #{c.name}, #{c.phone})")
    void insert(@Param("memberId") Long memberId, @Param("c") EmergencyContact contact);
}
