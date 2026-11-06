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
    List<String> findCampIds(@Param("listId") Long listId);

    // 이미 담긴 캠핑장이면 아무 일도 하지 않는다 (두 번 눌러도 오류 없음)
    @Insert("""
            INSERT INTO saved_camp (saved_list_id, camp_id) VALUES (#{listId}, #{campId})
            ON CONFLICT (saved_list_id, camp_id) DO NOTHING
            """)
    int insertCamp(@Param("listId") Long listId, @Param("campId") String campId);

    @Delete("DELETE FROM saved_camp WHERE saved_list_id = #{listId} AND camp_id = #{campId}")
    int deleteCamp(@Param("listId") Long listId, @Param("campId") String campId);
}
