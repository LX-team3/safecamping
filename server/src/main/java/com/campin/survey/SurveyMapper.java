package com.campin.survey;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// survey 관련 테이블 SQL. 테이블 정의는 db/schema.sql, 질문 데이터는 db/seed_survey.sql
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface SurveyMapper {

    // 컬럼은 snake_case, 자바 필드는 camelCase 라서 AS 로 이름을 맞춘다
    @Select("""
            SELECT id, code, title, duration_text AS durationText,
                   finish_label AS finishLabel, is_numbered AS numbered
              FROM survey
             WHERE code = #{code}
            """)
    Survey findSurveyByCode(@Param("code") String code);

    @Select("""
            SELECT id, survey_id AS surveyId, step, title, sub, note
              FROM survey_step
             WHERE survey_id = #{surveyId}
             ORDER BY step
            """)
    List<SurveyStep> findSteps(@Param("surveyId") Long surveyId);

    // 설문 하나의 질문 전부 (단계 순 → 단계 안 순서)
    @Select("""
            SELECT q.id, q.step_id AS stepId, q.code, q.sort_order AS sortOrder, q.title,
                   q.input_type AS inputType, q.max_select AS maxSelect,
                   q.is_required AS required, q.note
              FROM survey_question q
              JOIN survey_step st ON st.id = q.step_id
             WHERE st.survey_id = #{surveyId}
             ORDER BY st.step, q.sort_order
            """)
    List<SurveyQuestion> findQuestions(@Param("surveyId") Long surveyId);

    @Select("""
            SELECT o.id, o.question_id AS questionId, o.code, o.sort_order AS sortOrder,
                   o.label, o.description, o.is_exclusive AS exclusive, o.warning_text AS warningText
              FROM survey_option o
              JOIN survey_question q ON q.id = o.question_id
              JOIN survey_step st ON st.id = q.step_id
             WHERE st.survey_id = #{surveyId}
             ORDER BY o.question_id, o.sort_order
            """)
    List<SurveyOption> findOptions(@Param("surveyId") Long surveyId);

    // 로그인 아이디로 member.id 를 찾아 넣는다 → 회원이 없으면 0줄이 들어가고 0 을 돌려준다
    // @Options: INSERT 후 DB 가 만든 id 를 answer.id 에 다시 채워 준다
    @Insert("""
            INSERT INTO survey_answer (survey_id, member_id)
            SELECT #{surveyId}, id FROM member WHERE login_id = #{loginId}
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertAnswer(SurveyAnswer answer);

    @Insert("""
            INSERT INTO survey_answer_item (answer_id, option_id, rank)
            VALUES (#{answerId}, #{optionId}, #{rank})
            """)
    void insertAnswerItem(@Param("answerId") Long answerId, @Param("optionId") Long optionId, @Param("rank") int rank);

    // 이 회원이 이 설문에 낸 가장 최근 답변의 선택지들
    @Select("""
            SELECT q.code AS questionCode, o.code AS optionCode, i.rank
              FROM survey_answer_item i
              JOIN survey_option o ON o.id = i.option_id
              JOIN survey_question q ON q.id = o.question_id
             WHERE i.answer_id = (
                    SELECT a.id
                      FROM survey_answer a
                      JOIN member m ON m.id = a.member_id
                     WHERE a.survey_id = #{surveyId} AND m.login_id = #{loginId}
                     ORDER BY a.created_at DESC, a.id DESC
                     LIMIT 1)
             ORDER BY q.id, i.rank
            """)
    List<SurveyAnswerItem> findLatestAnswerItems(@Param("surveyId") Long surveyId, @Param("loginId") String loginId);
}
