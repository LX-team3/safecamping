package com.campin.nearby;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// 내 주변 SQL. 테이블 정의는 db/schema_02.sql (camp · facility · camp_facility)
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface NearbyMapper {

    // 내 위치에서 가장 가까운 캠핑장 하나 (주변 시설 데이터가 있는 곳만)
    // 순서만 비교하면 되므로 위도·경도 차이로 어림한다 (경도 1도는 위도가 높을수록 짧아져 cos 를 곱한다)
    // PostGIS 로 옮기면 ST_Distance 로 바꾼다
    @Select("""
            SELECT c.id, c.name, c.sido, c.sigungu, c.eupmyeon, c.lat, c.lng,
                   c.safety_score AS safetyScore, c.is_dummy AS dummy
              FROM camp c
             WHERE EXISTS (SELECT 1 FROM camp_facility cf WHERE cf.camp_id = c.id)
             ORDER BY power(c.lat - #{lat}, 2) + power((c.lng - #{lng}) * cos(radians(#{lat})), 2)
             LIMIT 1
            """)
    NearbyCamp findNearestCamp(@Param("lat") double lat, @Param("lng") double lng);

    // 그 캠핑장의 가까운 시설 (화면 번호 순서)
    @Select("""
            SELECT f.id, f.type, f.name, f.lat, f.lng,
                   cf.distance_km AS distanceKm, cf.drive_min AS driveMin,
                   (f.is_dummy OR cf.is_dummy) AS dummy
              FROM camp_facility cf
              JOIN facility f ON f.id = cf.facility_id
             WHERE cf.camp_id = #{campId}
             ORDER BY cf.sort_order
            """)
    List<NearbyFacilityRow> findFacilities(@Param("campId") Long campId);
}
