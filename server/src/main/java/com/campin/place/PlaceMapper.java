package com.campin.place;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// 캠핑장·시설·관광지 상세가 함께 쓰는 place_info · place_note 테이블 SQL
// placeType: "camp" | "facility" | "tour" — 어느 테이블의 id 인지 나타낸다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface PlaceMapper {

    String CAMP = "camp";
    String FACILITY = "facility";
    String TOUR = "tour";

    @Select("""
            SELECT label, value
              FROM place_info
             WHERE place_type = #{placeType} AND place_id = #{placeId}
             ORDER BY sort_order
            """)
    List<PlaceInfo> findInfos(@Param("placeType") String placeType, @Param("placeId") Long placeId);

    @Select("""
            SELECT level, body
              FROM place_note
             WHERE place_type = #{placeType} AND place_id = #{placeId}
             ORDER BY sort_order
            """)
    List<PlaceNote> findNotes(@Param("placeType") String placeType, @Param("placeId") Long placeId);
}
