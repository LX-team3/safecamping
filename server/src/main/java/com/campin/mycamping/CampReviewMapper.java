package com.campin.mycamping;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// camp_review · camp_review_photo 테이블 SQL. 테이블 정의는 db/schema_06.sql
// 읽는 SQL 에는 member_id 조건을 붙인다 → 남의 일정 id 를 보내도 보이지 않는다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface CampReviewMapper {

    // 내 후기 전부 (내 예약 화면이 한 번에 받는다)
    @Select("""
            SELECT id, plan_id AS planId, member_id AS memberId, content,
                   created_at AS createdAt, updated_at AS updatedAt
              FROM camp_review
             WHERE member_id = #{memberId}
             ORDER BY id
            """)
    List<CampReview> findByMemberId(@Param("memberId") Long memberId);

    // 일정 하나의 후기 (없으면 null)
    @Select("""
            SELECT id, plan_id AS planId, member_id AS memberId, content,
                   created_at AS createdAt, updated_at AS updatedAt
              FROM camp_review
             WHERE member_id = #{memberId} AND plan_id = #{planId}
            """)
    CampReview findByPlanId(@Param("memberId") Long memberId, @Param("planId") Long planId);

    // 내 후기들의 사진 전부 (후기별 · 순서대로)
    @Select("""
            SELECT p.id, p.review_id AS reviewId, p.file_name AS fileName, p.sort_order AS sortOrder
              FROM camp_review_photo p
              JOIN camp_review r ON r.id = p.review_id
             WHERE r.member_id = #{memberId}
             ORDER BY p.review_id, p.sort_order, p.id
            """)
    List<CampReviewPhoto> findPhotosByMemberId(@Param("memberId") Long memberId);

    // 후기 하나의 사진 (순서대로)
    @Select("""
            SELECT id, review_id AS reviewId, file_name AS fileName, sort_order AS sortOrder
              FROM camp_review_photo
             WHERE review_id = #{reviewId}
             ORDER BY sort_order, id
            """)
    List<CampReviewPhoto> findPhotos(@Param("reviewId") Long reviewId);

    // 사진 한 장 — 내 일정의 후기 사진일 때만 (남의 것이면 null)
    @Select("""
            SELECT p.id, p.review_id AS reviewId, p.file_name AS fileName, p.sort_order AS sortOrder
              FROM camp_review_photo p
              JOIN camp_review r ON r.id = p.review_id
             WHERE r.member_id = #{memberId} AND r.plan_id = #{planId} AND p.id = #{photoId}
            """)
    CampReviewPhoto findPhoto(@Param("memberId") Long memberId, @Param("planId") Long planId,
                              @Param("photoId") Long photoId);

    // 일정 하나에 딸린 사진 파일 이름 (일정을 지울 때 파일도 지우려고)
    @Select("""
            SELECT p.file_name
              FROM camp_review_photo p
              JOIN camp_review r ON r.id = p.review_id
             WHERE r.member_id = #{memberId} AND r.plan_id = #{planId}
            """)
    List<String> findFileNamesByPlan(@Param("memberId") Long memberId, @Param("planId") Long planId);

    // @Options: INSERT 후 DB 가 만든 id 를 review.id 에 다시 채워 준다
    @Insert("""
            INSERT INTO camp_review (plan_id, member_id, content)
            VALUES (#{planId}, #{memberId}, #{content})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(CampReview review);

    @Update("UPDATE camp_review SET content = #{content}, updated_at = now() WHERE id = #{id}")
    int updateContent(@Param("id") Long id, @Param("content") String content);

    @Insert("""
            INSERT INTO camp_review_photo (review_id, file_name, sort_order)
            VALUES (#{reviewId}, #{fileName}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertPhoto(CampReviewPhoto photo);

    @Update("UPDATE camp_review_photo SET sort_order = #{sortOrder} WHERE id = #{id}")
    int updatePhotoOrder(@Param("id") Long id, @Param("sortOrder") int sortOrder);

    @Delete("DELETE FROM camp_review_photo WHERE id = #{id}")
    int deletePhoto(@Param("id") Long id);
}
