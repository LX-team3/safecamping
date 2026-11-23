package com.campin.mycamping;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// camp_plan 테이블 SQL. 테이블 정의는 db/schema.sql
// 모든 SQL 에 member_id 조건을 붙인다 → 남의 등록 id 를 보내도 보이지도 지워지지도 않는다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface CampPlanMapper {

    // 체크인이 빠른 순 (같은 날이면 먼저 등록한 것부터)
    @Select("""
            SELECT id, member_id AS memberId, camp_id AS campId, camp_name AS campName, place, lat, lng,
                   check_in AS checkIn, check_out AS checkOut, memo
              FROM camp_plan
             WHERE member_id = #{memberId}
             ORDER BY check_in, id
            """)
    List<CampPlan> findByMemberId(@Param("memberId") Long memberId);

    // 내 일정 하나 (남의 id 는 null). 출발 준비 체크리스트가 쓴다
    @Select("""
            SELECT id, member_id AS memberId, camp_id AS campId, camp_name AS campName, place, lat, lng,
                   check_in AS checkIn, check_out AS checkOut, memo
              FROM camp_plan
             WHERE id = #{planId} AND member_id = #{memberId}
            """)
    CampPlan findById(@Param("memberId") Long memberId, @Param("planId") Long planId);

    @Select("""
            SELECT EXISTS (SELECT 1 FROM camp_plan
                            WHERE member_id = #{memberId} AND camp_id = #{campId} AND check_in = #{checkIn})
            """)
    boolean exists(@Param("memberId") Long memberId, @Param("campId") Long campId, @Param("checkIn") LocalDate checkIn);

    // 캠핑장이 아닌 자리(차박)의 중복: 같은 좌표 · 같은 체크인 (DB 의 camp_plan_spot_uq 와 같은 조건)
    @Select("""
            SELECT EXISTS (SELECT 1 FROM camp_plan
                            WHERE member_id = #{memberId} AND camp_id IS NULL
                              AND lat = #{lat} AND lng = #{lng} AND check_in = #{checkIn})
            """)
    boolean existsSpot(@Param("memberId") Long memberId, @Param("lat") double lat, @Param("lng") double lng,
                       @Param("checkIn") LocalDate checkIn);

    // @Options: INSERT 후 DB 가 만든 id 를 plan.id 에 다시 채워 준다
    @Insert("""
            INSERT INTO camp_plan (member_id, camp_id, camp_name, place, lat, lng, check_in, check_out, memo)
            VALUES (#{memberId}, #{campId}, #{campName}, #{place}, #{lat}, #{lng}, #{checkIn}, #{checkOut}, #{memo})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(CampPlan plan);

    // 날짜 · 메모만 고친다 (member_id 조건으로 남의 일정은 0줄)
    @Update("""
            UPDATE camp_plan
               SET check_in = #{checkIn}, check_out = #{checkOut}, memo = #{memo}
             WHERE id = #{id} AND member_id = #{memberId}
            """)
    int update(CampPlan plan);

    @Delete("DELETE FROM camp_plan WHERE id = #{planId} AND member_id = #{memberId}")
    int delete(@Param("memberId") Long memberId, @Param("planId") Long planId);
}
