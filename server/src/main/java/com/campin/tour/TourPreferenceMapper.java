package com.campin.tour;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// member_tour_preference 테이블 SQL. 테이블 정의는 db/schema.sql
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface TourPreferenceMapper {

    @Select("""
            SELECT question, option_code AS optionCode
              FROM member_tour_preference
             WHERE member_id = #{memberId}
            """)
    List<TourPreference> findByMemberId(@Param("memberId") Long memberId);

    // 저장할 때는 통째로 바꾼다: 모두 지우고 → 고른 것만 다시 넣는다
    @Delete("DELETE FROM member_tour_preference WHERE member_id = #{memberId}")
    void deleteByMemberId(@Param("memberId") Long memberId);

    @Insert("""
            INSERT INTO member_tour_preference (member_id, question, option_code)
            VALUES (#{memberId}, #{question}, #{optionCode})
            """)
    void insert(@Param("memberId") Long memberId, @Param("question") String question,
                @Param("optionCode") String optionCode);
}
