package com.campin.score;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// 안전점수 SQL. 테이블 구조는 바꾸지 않고, 이미 있는 칸의 값만 고친다
// 읽기: camp · camp_facility · facility · public.facilities · public.score_weights (public.* 는 공용 원본이라 읽기만)
// 쓰기: camp.safety_score · er_drive_min, camp_facility.drive_min (항상 WHERE 로 한 줄만)
//       camp_facility.distance_km(화면의 km)는 직선거리 그대로 둔다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface ScoreMapper {

    @Select("SELECT id, name, lat::float8 AS lat, lng::float8 AS lng FROM camp ORDER BY id")
    List<ScoreCamp> findCamps();

    @Select("""
            SELECT facility_type AS type, weight::float8 AS weight,
                   full_dist_m::float8 AS fullDistM, zero_dist_m::float8 AS zeroDistM
              FROM public.score_weights
            """)
    List<ScoreWeight> findWeights();

    @Select("""
            SELECT cf.facility_id AS facilityId, f.type, f.lat::float8 AS lat, f.lng::float8 AS lng
              FROM camp_facility cf
              JOIN facility f ON f.id = cf.facility_id
             WHERE cf.camp_id = #{campId}
            """)
    List<ScoreCampFacility> findCampFacilities(@Param("campId") Long campId);

    // 종류마다 가장 가까운 시설까지 직선거리(m). 시설 고르는 조건은 db/merge_to_public.sql 8 단계와 같다
    // 거리 식은 하버사인 공식 (merge_to_public.sql 의 pg_temp.dist_m 과 같은 식. 그 함수는 SQL 실행 중에만 있어서 여기 직접 쓴다)
    @Select("""
            SELECT f.type,
                   min(2 * 6371000 * asin(sqrt(
                         power(sin(radians(f.lat::float8 - #{lat}) / 2), 2)
                       + cos(radians(#{lat})) * cos(radians(f.lat::float8))
                         * power(sin(radians(f.lng::float8 - #{lng}) / 2), 2)))) AS distM
              FROM public.facilities f
             WHERE f.is_active AND f.lat IS NOT NULL
               AND f.type IN (SELECT facility_type FROM public.score_weights)
               AND NOT (f.type = 'cctv' AND f.role <> 'safety')
             GROUP BY f.type
            """)
    List<ScoreNearest> findNearest(@Param("lat") double lat, @Param("lng") double lng);

    @Update("""
            UPDATE camp_facility SET drive_min = #{driveMin}
             WHERE camp_id = #{campId} AND facility_id = #{facilityId}
            """)
    int updateDriveMin(@Param("campId") Long campId, @Param("facilityId") Long facilityId,
                       @Param("driveMin") int driveMin);

    @Update("UPDATE camp SET safety_score = #{score}, er_drive_min = #{erDriveMin} WHERE id = #{campId}")
    int updateCampScore(@Param("campId") Long campId, @Param("score") int score,
                        @Param("erDriveMin") Integer erDriveMin);
}
