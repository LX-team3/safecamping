package com.campin.place;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 상세 화면 정보 줄 하나 (place_info). 조회 결과를 그대로 JSON 응답으로 쓴다
// label 이 없으면 값만 한 줄로, value 가 없으면 화면에서 "확인 필요"로 보인다
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 빈 객체를 만든 뒤 값을 채운다
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class PlaceInfo {
    private String label;
    private String value;
}
