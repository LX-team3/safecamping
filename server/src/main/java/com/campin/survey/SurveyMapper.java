package com.campin.survey;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// survey_answer 테이블 SQL. 테이블 정의는 db/schema.sql
@Mapper
public interface SurveyMapper {

    @Select("""
            SELECT member_id AS memberId, camp_type AS campType, transport, heating,
                   safety_items AS safetyItems, facilities
              FROM survey_answer
             WHERE member_id = #{memberId}
            """)
    SurveyAnswerRow findByMemberId(@Param("memberId") Long memberId);

    // 회원당 한 줄: 처음이면 넣고, 이미 있으면(다시 진단) 덮어쓴다 (ON CONFLICT … DO UPDATE)
    @Insert("""
            INSERT INTO survey_answer (member_id, camp_type, transport, heating, safety_items, facilities)
            VALUES (#{memberId}, #{campType}, #{transport}, #{heating}, #{safetyItems}, #{facilities})
            ON CONFLICT (member_id) DO UPDATE
               SET camp_type = EXCLUDED.camp_type, transport = EXCLUDED.transport, heating = EXCLUDED.heating,
                   safety_items = EXCLUDED.safety_items, facilities = EXCLUDED.facilities, updated_at = now()
            """)
    void upsert(SurveyAnswerRow row);
}
