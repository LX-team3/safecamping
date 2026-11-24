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

    // 찍은 지점의 안전점수 재료: 종류마다 가장 가까운 시설까지 직선거리 + 부분점수
    // db/merge_to_public.sql 8 단계와 같은 계산 — 기준값은 팀이 넣은 raw.score_weights 그대로
    // (지금 캠핑장 점수 camp.safety_score 는 score/SafetyScoreService 가 카카오 차 시간 + ScoreRules 로 다시 계산해서 이 계산과 다르다)
    //  - 쓰는 시설: 운영 중(is_active)이고 좌표가 있는 것, CCTV 는 방범용(role='safety')만
    //  - 데이터가 하나도 없는 종류는 빼고 계산 (점수 = 받은 가중치 합 ÷ 있는 종류 가중치 합)
    //  - 거리: 지구를 공으로 본 직선거리(하버사인, m) — merge 의 pg_temp.dist_m 과 같은 식
    @Select("""
            WITH src AS (
                SELECT type, lat::float8 AS lat, lng::float8 AS lng
                  FROM raw.facilities
                 WHERE is_active AND lat IS NOT NULL
                   AND type IN (SELECT facility_type FROM raw.score_weights)
                   AND NOT (type = 'cctv' AND role <> 'safety')
            ),
            w AS (
                SELECT sw.* FROM raw.score_weights sw
                 WHERE EXISTS (SELECT 1 FROM src WHERE src.type = sw.facility_type)
            ),
            nearest AS (
                SELECT w.facility_type AS type, w.weight, w.full_dist_m, w.zero_dist_m,
                       min(2 * 6371000 * asin(sqrt(
                             power(sin(radians(s.lat - #{lat}) / 2), 2)
                           + cos(radians(#{lat})) * cos(radians(s.lat)) * power(sin(radians(s.lng - #{lng}) / 2), 2)))) AS d
                  FROM w JOIN src s ON s.type = w.facility_type
                 GROUP BY w.facility_type, w.weight, w.full_dist_m, w.zero_dist_m
            )
            SELECT type, weight::float8 AS weight, d AS distM,
                   CASE WHEN d <= full_dist_m THEN 1
                        WHEN d >= zero_dist_m THEN 0
                        ELSE (zero_dist_m - d) / (zero_dist_m - full_dist_m) END::float8 AS part
              FROM nearest
             ORDER BY weight DESC, type
            """)
    List<ChabakScoreRow> findScoreParts(@Param("lat") double lat, @Param("lng") double lng);

    // 근처 등록 야영장 이름 → camp.id (판정 결과에는 이름만 있어서 캠핑장 상세로 가려면 id 가 필요)
    // chabak.campsite 는 public.camp 이름 그대로 채운 것이라 이름으로 맞춘다 (채우는 절차: db/chabak_pipeline/README.md "공용 DB 반영" 5단계)
    @Select("""
            <script>
            SELECT id, name FROM camp WHERE name IN
            <foreach collection="names" item="n" open="(" separator="," close=")">#{n}</foreach>
            </script>
            """)
    List<ChabakLayerRow> findCampsByName(@Param("names") List<String> names);

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
    // 이름: 지번 끝의 지목 한 글자 "잡"을 "잡종지"로 풀어 쓴다 (후보지는 전부 잡종지 — 05_move_to_chabak_schema.sql 조건)
    @Select("""
            SELECT regexp_replace(o.jibun, '[[:space:]]*잡$', '') || ' 잡종지' AS name,
                   o.owner_nm AS area,
                   extensions.ST_Y(extensions.ST_Transform(o.geom, 4326)) AS lat,
                   extensions.ST_X(extensions.ST_Transform(o.geom, 4326)) AS lng
              FROM chabak.open_spot o
             ORDER BY o.radius_m DESC
             LIMIT 1000
            """)
    List<ChabakLayerRow> findOpenSpots();

    // 등록 야영장: camp 테이블 (chabak.campsite 도 여기서 채운 것). id 로 캠핑장 상세에 갈 수 있다
    @Select("""
            SELECT id, name, lat::float8 AS lat, lng::float8 AS lng, safety_score AS score,
                   concat_ws(' ', sigungu, eupmyeon) AS area, image_url AS imageUrl, er_drive_min AS erDriveMin
              FROM camp
             WHERE lat IS NOT NULL AND lng IS NOT NULL
            """)
    List<ChabakLayerRow> findCamps();
}
