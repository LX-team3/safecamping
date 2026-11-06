package com.campin.family;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// family_schedule 테이블 SQL. 테이블 정의는 db/schema.sql
// 모든 SQL 에 family_room_id 조건을 붙인다 → 다른 가족방의 일정 id 를 보내도 지울 수 없다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface ScheduleMapper {

    // 시간 순서대로 (같은 시간이면 먼저 넣은 것부터)
    @Select("""
            SELECT s.id, s.family_room_id AS familyRoomId, s.title, s.start_at AS startAt, s.memo,
                   s.created_by AS createdBy, m.name AS createdByName
              FROM family_schedule s
              LEFT JOIN member m ON m.id = s.created_by
             WHERE s.family_room_id = #{roomId}
             ORDER BY s.start_at, s.id
            """)
    List<FamilySchedule> findAll(@Param("roomId") Long roomId);

    // @Options: INSERT 후 DB 가 만든 id 를 schedule.id 에 다시 채워 준다
    @Insert("""
            INSERT INTO family_schedule (family_room_id, title, start_at, memo, created_by)
            VALUES (#{familyRoomId}, #{title}, #{startAt}, #{memo}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(FamilySchedule schedule);

    @Delete("DELETE FROM family_schedule WHERE id = #{scheduleId} AND family_room_id = #{roomId}")
    int delete(@Param("roomId") Long roomId, @Param("scheduleId") Long scheduleId);
}
