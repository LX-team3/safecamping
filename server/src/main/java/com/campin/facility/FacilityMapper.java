package com.campin.facility;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// facility 테이블 SQL. 테이블 정의는 db/schema.sql, 더미 데이터는 db/data_detail.sql
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface FacilityMapper {

    @Select("""
            SELECT id, type, name, sido, sigungu, eupmyeon, address, phone, hours,
                   lat, lng, is_dummy AS dummy
              FROM facility
             WHERE id = #{id}
            """)
    Facility findById(@Param("id") Long id);

    // 이 캠핑장과 연결된 시설이 아니면 null
    @Select("""
            SELECT c.id AS campId, c.name AS campName, c.lat, c.lng,
                   cf.distance_km AS distanceKm, cf.drive_min AS driveMin,
                   c.safety_score AS safetyScore
              FROM camp_facility cf
              JOIN camp c ON c.id = cf.camp_id
             WHERE cf.facility_id = #{facilityId} AND cf.camp_id = #{campId}
            """)
    FacilityFrom findFrom(@Param("facilityId") Long facilityId, @Param("campId") Long campId);

    // 지도 화면 안(남서·북동 모서리 사이)에 있는 시설 — 고른 종류만
    // 넓게 보면 점이 너무 많아지므로 화면 가운데에서 가까운 순으로 limit 곳까지만 (멀리 있는 점부터 빠짐)
    @Select("""
            <script>
            SELECT id, type, name, lat, lng, is_dummy AS dummy
              FROM facility
             WHERE type IN
                   <foreach collection="types" item="t" open="(" separator="," close=")">#{t}</foreach>
               AND lat BETWEEN #{swLat} AND #{neLat}
               AND lng BETWEEN #{swLng} AND #{neLng}
             ORDER BY power(lat::float8 - (#{swLat} + #{neLat}) / 2, 2)
                    + power(lng::float8 - (#{swLng} + #{neLng}) / 2, 2)
             LIMIT #{limit}
            </script>
            """)
    List<FacilityPin> findInBounds(@Param("types") List<String> types,
                                   @Param("swLat") double swLat, @Param("swLng") double swLng,
                                   @Param("neLat") double neLat, @Param("neLng") double neLng,
                                   @Param("limit") int limit);
}
