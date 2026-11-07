package com.campin.region;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// sido · sigungu 테이블 SQL. 테이블 정의는 db/schema.sql, 데이터는 db/seed_region.sql
// 조회 결과를 바로 응답 객체(SidoResponse·SigunguResponse)에 채운다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface RegionMapper {

    // 시·도 + 들어 있는 시·군·구 수 (0 이면 화면에서 "준비 중")
    @Select("""
            SELECT s.id, s.name, COUNT(g.id) AS sigunguCount
              FROM sido s
              LEFT JOIN sigungu g ON g.sido_id = s.id
             GROUP BY s.id, s.name, s.sort_order
             ORDER BY s.sort_order
            """)
    List<SidoResponse> findSidos();

    @Select("SELECT EXISTS (SELECT 1 FROM sido WHERE id = #{sidoId})")
    boolean existsSido(@Param("sidoId") Long sidoId);

    @Select("""
            SELECT g.id, s.name AS sido, g.name, g.is_safety_ready AS safetyReady
              FROM sigungu g
              JOIN sido s ON s.id = g.sido_id
             WHERE g.sido_id = #{sidoId}
             ORDER BY g.sort_order
            """)
    List<SigunguResponse> findSigungus(@Param("sidoId") Long sidoId);

    // 이름 일부로 검색 (예: "인제" → 인제군). || 는 문자열 잇기
    @Select("""
            SELECT g.id, s.name AS sido, g.name, g.is_safety_ready AS safetyReady
              FROM sigungu g
              JOIN sido s ON s.id = g.sido_id
             WHERE g.name LIKE '%' || #{q} || '%'
             ORDER BY s.sort_order, g.sort_order
             LIMIT 30
            """)
    List<SigunguResponse> searchSigungus(@Param("q") String q);
}
