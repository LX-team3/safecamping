package com.campin.mycamping;

import java.time.OffsetDateTime;
import java.util.List;

// 지난 캠핑 후기 하나. photos 의 url 은 이 서버의 사진 주소 (로그인한 본인만 열 수 있음)
public record CampReviewResponse(Long planId, String content, List<Photo> photos,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public record Photo(Long id, String url) {
    }

    public static CampReviewResponse of(CampReview r, List<CampReviewPhoto> photos) {
        List<Photo> list = photos.stream()
                .map(p -> new Photo(p.getId(), photoUrl(r.getPlanId(), p.getId())))
                .toList();
        return new CampReviewResponse(r.getPlanId(), r.getContent(), list, r.getCreatedAt(), r.getUpdatedAt());
    }

    static String photoUrl(Long planId, Long photoId) {
        return "/api/member/camp-plans/" + planId + "/review/photos/" + photoId;
    }
}
