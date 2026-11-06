package com.campin.family;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// child_card 테이블 SQL. 테이블 정의는 db/schema.sql
// 모든 SQL 에 family_room_id 조건을 붙인다 → 다른 가족방의 카드 id 를 보내도 읽거나 바꿀 수 없다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface ChildCardMapper {

    // 먼저 만든 카드부터
    @Select("""
            SELECT c.id, c.family_room_id AS familyRoomId, c.name, c.birth_date AS birthDate,
                   c.blood_type AS bloodType, c.allergy, c.disease, c.medicine, c.memo,
                   c.updated_by AS updatedBy, m.name AS updatedByName, c.updated_at AS updatedAt
              FROM child_card c
              LEFT JOIN member m ON m.id = c.updated_by
             WHERE c.family_room_id = #{roomId}
             ORDER BY c.id
            """)
    List<ChildCard> findAll(@Param("roomId") Long roomId);

    // @Options: INSERT 후 DB 가 만든 id 를 card.id 에 다시 채워 준다
    @Insert("""
            INSERT INTO child_card (family_room_id, name, birth_date, blood_type, allergy, disease, medicine, memo, updated_by)
            VALUES (#{familyRoomId}, #{name}, #{birthDate}, #{bloodType}, #{allergy}, #{disease}, #{medicine}, #{memo}, #{updatedBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ChildCard card);

    // 다른 방 카드이거나 없는 카드면 0줄
    @Update("""
            UPDATE child_card
               SET name = #{name}, birth_date = #{birthDate}, blood_type = #{bloodType}, allergy = #{allergy},
                   disease = #{disease}, medicine = #{medicine}, memo = #{memo},
                   updated_by = #{updatedBy}, updated_at = now()
             WHERE id = #{id} AND family_room_id = #{familyRoomId}
            """)
    int update(ChildCard card);

    @Delete("DELETE FROM child_card WHERE id = #{cardId} AND family_room_id = #{roomId}")
    int delete(@Param("roomId") Long roomId, @Param("cardId") Long cardId);
}
