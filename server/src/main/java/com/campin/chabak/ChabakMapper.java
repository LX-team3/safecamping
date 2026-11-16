package com.campin.chabak;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// 차박 판정 SQL. 판정 규칙은 전부 DB 의 chabak 스키마 함수에 있다 (fn_verdict → fn_verdict_json)
// 규칙을 바꿀 때는 DB 함수만 고치면 되고 이 코드는 그대로 둔다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface ChabakMapper {

    // 찍은 지점의 판정 결과 JSON 문자열. 인제군 밖(20m 안에 필지 없음)이면 null
    @Select("SELECT chabak.fn_verdict_json(#{lat}, #{lng})::text")
    String findVerdict(@Param("lat") double lat, @Param("lng") double lng);

    // 찍은 지점에서 종류별로 가까운 안전 시설 3곳씩 (차박 지도의 "지도에 보일 것" 레이어)
    // 거리는 위도·경도 차이로 어림한다 (경도 1도는 위도가 높을수록 짧아져 cos 를 곱함, 1도 ≈ 111.32km) — NearbyMapper 와 같은 방식
    @Select("""
            SELECT id, type, name, lat, lng, distance_km AS distanceKm, dummy
              FROM (SELECT f.id, f.type, f.name, f.lat, f.lng, f.is_dummy AS dummy,
                           round((111.32 * sqrt(power(f.lat::float8 - #{lat}, 2)
                                  + power((f.lng::float8 - #{lng}) * cos(radians(#{lat})), 2)))::numeric, 1) AS distance_km,
                           row_number() OVER (PARTITION BY f.type
                                ORDER BY power(f.lat::float8 - #{lat}, 2)
                                       + power((f.lng::float8 - #{lng}) * cos(radians(#{lat})), 2)) AS rn
                      FROM facility f
                     WHERE f.type IN ('er', 'fire', 'police', 'store', 'pharmacy')) x
             WHERE rn <= 3
             ORDER BY distance_km
            """)
    List<ChabakFacility> findFacilities(@Param("lat") double lat, @Param("lng") double lng);

    // ---- 차박 지도 레이어 (칩: 불가 구역 · 주의 구역 · 차박 후보지 · 등록 야영장) ----
    // PostGIS 함수는 공유 DB 에서 extensions 스키마에 있다 → 접속 기본 스키마(public)에서 못 찾으므로 extensions. 를 붙인다
    // 좌표계: DB 는 EPSG:5186(미터) → 화면은 위도·경도(4326) 로 바꿔 보낸다

    // 규제 구역은 화면 표시용으로 한 덩어리로 합쳐 보낸다 (판정에는 안 씀 — 판정은 원본 경계로 DB 함수가)
    //  - 국립공원·백두대간·산림보호구역은 같은 땅에 겹쳐 지정된 곳이 많다 → 따로 보내면 반투명 면이 여러 겹 쌓여 어지럽다
    //  - 경계는 20m 단위로 먼저 단순화한 뒤 합친다 (합치는 계산이 가벼워짐)
    //  - ST_CollectionExtract(…, 3): 합친 결과에서 면만 남긴다 (선·점 조각 제거)

    // 불가 구역 한 덩어리
    @Select("""
            SELECT '불가 구역' AS name,
                   extensions.ST_AsGeoJSON(extensions.ST_Transform(
                       extensions.ST_CollectionExtract(
                           extensions.ST_Union(extensions.ST_SimplifyPreserveTopology(r.geom, 20)), 3), 4326), 6) AS geojson
              FROM chabak.regulation r
             WHERE r.level = '불가'
            """)
    ChabakLayerRow findNoZone();

    // 주의 구역 한 덩어리. 불가 구역과 겹친 부분은 뺀다 → 한 땅에 한 색만 (불가가 우선)
    @Select("""
            WITH c AS (SELECT extensions.ST_Union(extensions.ST_SimplifyPreserveTopology(geom, 20)) AS g
                         FROM chabak.regulation WHERE level = '주의'),
                 n AS (SELECT extensions.ST_Union(extensions.ST_SimplifyPreserveTopology(geom, 20)) AS g
                         FROM chabak.regulation WHERE level = '불가')
            SELECT '주의 구역' AS name,
                   extensions.ST_AsGeoJSON(extensions.ST_Transform(
                       extensions.ST_CollectionExtract(
                           COALESCE(extensions.ST_Difference(c.g, n.g), c.g), 3), 4326), 6) AS geojson
              FROM c, n
            """)
    ChabakLayerRow findCautionZone();

    // 차박 후보지: 미리 계산해 둔 '제한 없음' 자리 (chabak.open_spot, 국공유 공터 · 차 댈 공간 반지름 5m 이상)
    // 혹시 너무 많아도 지도가 느려지지 않게 넓은 자리부터 1000곳까지
    @Select("""
            SELECT o.jibun AS name,
                   extensions.ST_Y(extensions.ST_Transform(o.geom, 4326)) AS lat,
                   extensions.ST_X(extensions.ST_Transform(o.geom, 4326)) AS lng
              FROM chabak.open_spot o
             ORDER BY o.radius_m DESC
             LIMIT 1000
            """)
    List<ChabakLayerRow> findOpenSpots();

    // 등록 야영장: camp 테이블 (chabak.campsite 도 여기서 채운 것). id 로 캠핑장 상세에 갈 수 있다
    @Select("""
            SELECT id, name, lat::float8 AS lat, lng::float8 AS lng
              FROM camp
             WHERE lat IS NOT NULL AND lng IS NOT NULL
            """)
    List<ChabakLayerRow> findCamps();
}
