package com.campin.camp;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// camp 테이블 SQL. 테이블 정의는 db/schema.sql, 더미 데이터는 db/seed_camp.sql
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface CampMapper {

    // <script>: MyBatis 동적 SQL. <if> 조건이 맞을 때만 그 줄이 붙고, <where> 가 맨 앞 AND 를 알아서 지운다
    // 정렬은 서비스에서 한다 (등급·정렬 규칙을 테스트로 확인하기 쉽게)
    @Select("""
            <script>
            SELECT id, name, sido, sigungu, eupmyeon, camp_type AS campType, lat, lng,
                   safety_score AS safetyScore, er_drive_min AS erDriveMin,
                   image_url AS imageUrl, is_dummy AS dummy
              FROM camp
            <where>
              <if test="sido != null">AND sido = #{sido}</if>
              <if test="sigungu != null">AND sigungu = #{sigungu}</if>
              <if test="campType != null">AND camp_type = #{campType}</if>
            </where>
             ORDER BY id
            </script>
            """)
    List<Camp> findCamps(@Param("sido") String sido, @Param("sigungu") String sigungu,
                         @Param("campType") String campType);

    @Select("""
            SELECT id, name, sido, sigungu, eupmyeon, camp_type AS campType, lat, lng,
                   safety_score AS safetyScore, er_drive_min AS erDriveMin,
                   image_url AS imageUrl, is_dummy AS dummy,
                   reservation_url AS reservationUrl
              FROM camp
             WHERE id = #{id}
            """)
    Camp findById(@Param("id") Long id);

    // 캠핑장의 가까운 시설 (화면 번호 순서)
    @Select("""
            SELECT f.id, f.type, f.name, f.lat, f.lng,
                   cf.distance_km AS distanceKm, cf.drive_min AS driveMin
              FROM camp_facility cf
              JOIN facility f ON f.id = cf.facility_id
             WHERE cf.camp_id = #{campId}
             ORDER BY cf.sort_order
            """)
    List<CampFacilityRow> findFacilities(@Param("campId") Long campId);

    // 캠핑장의 주변 관광지 (정렬은 취향에 따라 서비스에서 한다)
    @Select("""
            SELECT t.id, t.name, t.category, t.nature, t.popularity_rank AS popularityRank,
                   ct.drive_min AS driveMin, ct.note
              FROM camp_tour ct
              JOIN tour t ON t.id = ct.tour_id
             WHERE ct.camp_id = #{campId}
            """)
    List<CampTourRow> findTours(@Param("campId") Long campId);
}
