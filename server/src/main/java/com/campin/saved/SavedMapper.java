package com.campin.saved;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// saved_list·saved_camp 테이블 SQL. 테이블 정의는 db/schema.sql
// 목록을 다루는 SQL 에는 항상 member_id 조건을 붙인다 → 남의 목록 id 를 보내도 읽거나 바꿀 수 없다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface SavedMapper {

    // 기본 목록을 맨 앞에, 나머지는 만든 순서대로
    @Select("""
            SELECT l.id, l.member_id AS memberId, l.name, l.is_default AS isDefault,
                   COUNT(c.id) AS campCount, l.created_at AS createdAt
              FROM saved_list l
              LEFT JOIN saved_camp c ON c.saved_list_id = l.id
             WHERE l.member_id = #{memberId}
             GROUP BY l.id
             ORDER BY l.is_default DESC, l.created_at, l.id
            """)
    List<SavedList> findLists(@Param("memberId") Long memberId);

    // 남의 목록이거나 없으면 null
    @Select("""
            SELECT id, member_id AS memberId, name, is_default AS isDefault, created_at AS createdAt
              FROM saved_list
             WHERE id = #{listId} AND member_id = #{memberId}
            """)
    SavedList findList(@Param("memberId") Long memberId, @Param("listId") Long listId);

    @Select("SELECT EXISTS (SELECT 1 FROM saved_list WHERE member_id = #{memberId} AND is_default)")
    boolean existsDefaultList(@Param("memberId") Long memberId);

    // '관심 캠핑장' 기본 목록 id (없으면 null)
    @Select("SELECT id FROM saved_list WHERE member_id = #{memberId} AND is_default")
    Long findDefaultListId(@Param("memberId") Long memberId);

    // camp 테이블에 있는 캠핑장인지 (없는 캠핑장이면 서비스에서 404 로 알려 준다. DB 외래키도 한 번 더 막는다)
    @Select("SELECT EXISTS (SELECT 1 FROM camp WHERE id = #{campId})")
    boolean existsCamp(@Param("campId") Long campId);

    @Select("SELECT EXISTS (SELECT 1 FROM saved_list WHERE member_id = #{memberId} AND name = #{name})")
    boolean existsListName(@Param("memberId") Long memberId, @Param("name") String name);

    // @Options: INSERT 후 DB 가 만든 id 를 list.id 에 다시 채워 준다
    // ON CONFLICT: 요청 두 개가 동시에 들어와 같은 이름이 겹쳐도 오류 대신 0줄 처리
    @Insert("""
            INSERT INTO saved_list (member_id, name, is_default)
            VALUES (#{memberId}, #{name}, #{isDefault})
            ON CONFLICT (member_id, name) DO NOTHING
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertList(SavedList list);

    // 기본 목록은 이름을 바꾸지 않는다 (is_default = false 조건)
    @Update("""
            UPDATE saved_list SET name = #{name}
             WHERE id = #{listId} AND member_id = #{memberId} AND is_default = false
            """)
    int updateListName(@Param("memberId") Long memberId, @Param("listId") Long listId, @Param("name") String name);

    // 기본 목록은 지우지 않는다. 담은 캠핑장은 ON DELETE CASCADE 로 같이 지워진다
    @Delete("DELETE FROM saved_list WHERE id = #{listId} AND member_id = #{memberId} AND is_default = false")
    int deleteList(@Param("memberId") Long memberId, @Param("listId") Long listId);

    // 담은 순서대로
    @Select("SELECT camp_id FROM saved_camp WHERE saved_list_id = #{listId} ORDER BY created_at, id")
    List<Long> findCampIds(@Param("listId") Long listId);

    // 비교 표 값. 담은 순서대로 (saved_camp.camp_id 는 camp.id 외래키라 항상 camp 에 있다 — db/schema_04.sql)
    // 소방서까지 시간은 camp_facility(캠핑장별 가까운 시설), 전기·반려동물은 원본 raw.camps 를 source_id 로 잇는다
    // 원본은 raw 스키마에 있다 (db/schema_03.sql 2단계). 접속 기본 스키마가 public 이라 raw. 를 꼭 붙인다
    @Select("""
            SELECT sc.camp_id AS id, c.name, c.safety_score AS safetyScore, c.er_drive_min AS erMinutes,
                   (SELECT min(cf.drive_min)
                      FROM camp_facility cf JOIN facility f ON f.id = cf.facility_id
                     WHERE cf.camp_id = c.id AND f.type = 'fire') AS fireMinutes,
                   src.has_electricity AS hasPower, src.pet_allowed AS allowsPet
              FROM saved_camp sc
              JOIN camp c ON c.id = sc.camp_id
              LEFT JOIN raw.camps src ON src.content_id = c.source_id
             WHERE sc.saved_list_id = #{listId}
             ORDER BY sc.created_at, sc.id
            """)
    List<CompareCamp> findCompareCamps(@Param("listId") Long listId);

    // 이미 담긴 캠핑장이면 아무 일도 하지 않는다 (두 번 눌러도 오류 없음)
    @Insert("""
            INSERT INTO saved_camp (saved_list_id, camp_id) VALUES (#{listId}, #{campId})
            ON CONFLICT (saved_list_id, camp_id) DO NOTHING
            """)
    int insertCamp(@Param("listId") Long listId, @Param("campId") Long campId);

    @Delete("DELETE FROM saved_camp WHERE saved_list_id = #{listId} AND camp_id = #{campId}")
    int deleteCamp(@Param("listId") Long listId, @Param("campId") Long campId);
}
