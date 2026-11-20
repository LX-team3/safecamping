package com.campin.mycamping;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// camp_review_photo 테이블 한 줄. 사진 파일은 ReviewPhotoStore 가 서버 폴더에 두고, 여기는 이름만
@Getter              // Lombok: getter 자동 생성
@Setter              // Lombok: setter 자동 생성 (MyBatis 가 값을 채울 때 씀)
@NoArgsConstructor   // Lombok: 빈 생성자 — MyBatis 가 객체를 만들 때 필요
public class CampReviewPhoto {
    private Long id;
    private Long reviewId;
    private String fileName;   // 서버가 만든 이름 (UUID + 확장자)
    private Integer sortOrder; // 화면 순서 (0 부터)
}
