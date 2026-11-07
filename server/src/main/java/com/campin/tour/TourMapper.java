package com.campin.tour;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// tour 테이블 SQL. 테이블 정의는 db/schema.sql, 더미 데이터는 db/data_detail.sql
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface TourMapper {

    @Select("""
            SELECT id, name, category_label AS categoryLabel, popularity_rank AS popularityRank,
                   sido, sigungu, eupmyeon, lat, lng, is_dummy AS dummy
              FROM tour
             WHERE id = #{id}
            """)
    Tour findById(@Param("id") Long id);

    // 이 캠핑장과 연결된 관광지가 아니면 null
    @Select("""
            SELECT c.id AS campId, c.name AS campName, c.lat, c.lng, ct.drive_min AS driveMin
              FROM camp_tour ct
              JOIN camp c ON c.id = ct.camp_id
             WHERE ct.tour_id = #{tourId} AND ct.camp_id = #{campId}
            """)
    TourFrom findFrom(@Param("tourId") Long tourId, @Param("campId") Long campId);
}
