package com.campin.score;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// 안전점수 SQL. 테이블 구조는 바꾸지 않고, 이미 있는 칸의 값만 고친다
// 읽기: camp · camp_facility · facility (직선거리·score_weights 는 더 이상 쓰지 않는다)
// 쓰기: camp.safety_score · er_drive_min, camp_facility.drive_min (항상 WHERE 로 한 줄만)
//       camp_facility.distance_km(화면의 km)는 직선거리 그대로 둔다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface ScoreMapper {

    @Select("SELECT id, name, lat::float8 AS lat, lng::float8 AS lng FROM camp ORDER BY id")
    List<ScoreCamp> findCamps();

    @Select("""
            SELECT cf.facility_id AS facilityId, f.type, f.lat::float8 AS lat, f.lng::float8 AS lng
              FROM camp_facility cf
              JOIN facility f ON f.id = cf.facility_id
             WHERE cf.camp_id = #{campId}
            """)
    List<ScoreCampFacility> findCampFacilities(@Param("campId") Long campId);

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
