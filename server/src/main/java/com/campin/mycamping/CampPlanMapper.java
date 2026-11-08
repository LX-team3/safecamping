package com.campin.mycamping;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// camp_plan 테이블 SQL. 테이블 정의는 db/schema.sql
// 모든 SQL 에 member_id 조건을 붙인다 → 남의 등록 id 를 보내도 보이지도 지워지지도 않는다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface CampPlanMapper {

    // 체크인이 빠른 순 (같은 날이면 먼저 등록한 것부터)
    @Select("""
            SELECT id, member_id AS memberId, camp_id AS campId, camp_name AS campName, place,
                   check_in AS checkIn, check_out AS checkOut, memo
              FROM camp_plan
             WHERE member_id = #{memberId}
             ORDER BY check_in, id
            """)
    List<CampPlan> findByMemberId(@Param("memberId") Long memberId);

    @Select("""
            SELECT EXISTS (SELECT 1 FROM camp_plan
                            WHERE member_id = #{memberId} AND camp_id = #{campId} AND check_in = #{checkIn})
            """)
    boolean exists(@Param("memberId") Long memberId, @Param("campId") Long campId, @Param("checkIn") LocalDate checkIn);

    // @Options: INSERT 후 DB 가 만든 id 를 plan.id 에 다시 채워 준다
    @Insert("""
            INSERT INTO camp_plan (member_id, camp_id, camp_name, place, check_in, check_out, memo)
            VALUES (#{memberId}, #{campId}, #{campName}, #{place}, #{checkIn}, #{checkOut}, #{memo})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(CampPlan plan);

    @Delete("DELETE FROM camp_plan WHERE id = #{planId} AND member_id = #{memberId}")
    int delete(@Param("memberId") Long memberId, @Param("planId") Long planId);
}
