package com.campin.facility;

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
                   cf.distance_km AS distanceKm, cf.drive_min AS driveMin
              FROM camp_facility cf
              JOIN camp c ON c.id = cf.camp_id
             WHERE cf.facility_id = #{facilityId} AND cf.camp_id = #{campId}
            """)
    FacilityFrom findFrom(@Param("facilityId") Long facilityId, @Param("campId") Long campId);
}
