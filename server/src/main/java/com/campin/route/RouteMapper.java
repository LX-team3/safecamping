package com.campin.route;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// 경로 SQL. 테이블 정의는 db/schema_02.sql (camp · facility · camp_facility)
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface RouteMapper {

    @Select("SELECT id, name, lat, lng FROM camp WHERE id = #{campId}")
    RouteCamp findCamp(@Param("campId") Long campId);

    // 경로를 그릴 시설 5종: 응급실·소방서·경찰서 + 편의점·약국 (2026-11-09 추가). 화장실만 빼고, 화면 번호 순서
    @Select("""
            SELECT f.id, f.type, f.name, f.lat, f.lng,
                   cf.distance_km AS distanceKm, cf.drive_min AS driveMin,
                   f.is_dummy AS dummy
              FROM camp_facility cf
              JOIN facility f ON f.id = cf.facility_id
             WHERE cf.camp_id = #{campId}
               AND f.type IN ('er', 'fire', 'police', 'store', 'pharmacy')
             ORDER BY cf.sort_order
            """)
    List<RouteFacility> findFacilities(@Param("campId") Long campId);
}
