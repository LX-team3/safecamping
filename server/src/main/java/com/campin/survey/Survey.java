package com.campin.survey;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// survey 테이블 한 줄 (설문 종류)
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 조회 결과를 채울 때 빈 객체가 필요
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class Survey {
    private Long id;
    private String code;          // diagnosis, travel
    private String title;
    private String durationText;  // 예: 약 30초 (없으면 null)
    private String finishLabel;   // 마지막 버튼 문구
    private boolean numbered;     // 질문 앞에 번호를 붙일지
}
