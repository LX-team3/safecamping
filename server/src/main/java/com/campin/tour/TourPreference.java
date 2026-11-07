package com.campin.tour;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// member_tour_preference 테이블 한 줄. 회원이 고른 선택지 하나
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 빈 객체를 만든 뒤 값을 채운다
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class TourPreference {
    private String question;    // activity 또는 nature
    private String optionCode;  // 고른 선택지 code
}
