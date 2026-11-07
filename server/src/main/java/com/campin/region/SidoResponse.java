package com.campin.region;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 시·도 하나 (조회 결과를 MyBatis 가 바로 채우고, 그대로 JSON 응답으로 쓴다)
// sigunguCount: DB 에 들어 있는 시·군·구 수 (0 이면 아직 목록 준비 중)
@Getter
@Setter
@NoArgsConstructor   // MyBatis 가 빈 객체를 만든 뒤 값을 채운다
@AllArgsConstructor  // 테스트에서 값을 한 번에 넣어 만들 때 쓴다
public class SidoResponse {
    private Long id;
    private String name;
    private long sigunguCount;
}
