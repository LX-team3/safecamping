package com.campin.mycamping;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 테스트용 camp_review · camp_review_photo (DB 대신 메모리 목록). SQL 과 같은 조건으로 거른다
class FakeCampReviewMapper implements CampReviewMapper {

    final List<CampReview> reviews = new ArrayList<>();
    final List<CampReviewPhoto> photos = new ArrayList<>();
    private long nextReviewId = 1;
    private long nextPhotoId = 1;

    private CampReview reviewOf(Long reviewId) {
        return reviews.stream().filter(r -> r.getId().equals(reviewId)).findFirst().orElse(null);
    }

    private List<CampReviewPhoto> sorted(List<CampReviewPhoto> list) {
        return list.stream().sorted(Comparator.comparing(CampReviewPhoto::getReviewId)
                .thenComparing(CampReviewPhoto::getSortOrder).thenComparing(CampReviewPhoto::getId)).toList();
    }

    @Override public List<CampReview> findByMemberId(Long memberId) {
        return reviews.stream().filter(r -> r.getMemberId().equals(memberId)).toList();
    }

    @Override public CampReview findByPlanId(Long memberId, Long planId) {
        return reviews.stream().filter(r -> r.getMemberId().equals(memberId) && r.getPlanId().equals(planId))
                .findFirst().orElse(null);
    }

    @Override public List<CampReviewPhoto> findPhotosByMemberId(Long memberId) {
        return sorted(photos.stream().filter(p -> reviewOf(p.getReviewId()).getMemberId().equals(memberId)).toList());
    }

    @Override public List<CampReviewPhoto> findPhotos(Long reviewId) {
        return sorted(photos.stream().filter(p -> p.getReviewId().equals(reviewId)).toList());
    }

    @Override public CampReviewPhoto findPhoto(Long memberId, Long planId, Long photoId) {
        return photos.stream().filter(p -> {
            CampReview r = reviewOf(p.getReviewId());
            return p.getId().equals(photoId) && r.getMemberId().equals(memberId) && r.getPlanId().equals(planId);
        }).findFirst().orElse(null);
    }

    @Override public List<String> findFileNamesByPlan(Long memberId, Long planId) {
        CampReview r = findByPlanId(memberId, planId);
        return r == null ? List.of() : findPhotos(r.getId()).stream().map(CampReviewPhoto::getFileName).toList();
    }

    @Override public int insert(CampReview review) {
        review.setId(nextReviewId++);
        review.setCreatedAt(OffsetDateTime.now());
        review.setUpdatedAt(review.getCreatedAt());
        reviews.add(review);
        return 1;
    }

    @Override public int updateContent(Long id, String content) {
        CampReview r = reviewOf(id);
        r.setContent(content);
        r.setUpdatedAt(OffsetDateTime.now());
        return 1;
    }

    @Override public int insertPhoto(CampReviewPhoto photo) {
        photo.setId(nextPhotoId++);
        photos.add(photo);
        return 1;
    }

    @Override public int updatePhotoOrder(Long id, int sortOrder) {
        photos.stream().filter(p -> p.getId().equals(id)).forEach(p -> p.setSortOrder(sortOrder));
        return 1;
    }

    @Override public int deletePhoto(Long id) {
        return photos.removeIf(p -> p.getId().equals(id)) ? 1 : 0;
    }

    // 일정을 지울 때 DB 의 ON DELETE CASCADE 흉내
    void cascadeDeletePlan(Long planId) {
        reviews.stream().filter(r -> r.getPlanId().equals(planId)).map(CampReview::getId).toList()
                .forEach(id -> photos.removeIf(p -> p.getReviewId().equals(id)));
        reviews.removeIf(r -> r.getPlanId().equals(planId));
    }
}
