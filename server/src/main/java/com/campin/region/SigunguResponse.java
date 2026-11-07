package com.campin.region;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 시·군·구 하나. safetyReady: 안심(안전점수) 데이터가 있는지 → 화면 "안심 데이터 있음"
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SigunguResponse {
    private Long id;
    private String sido;
    private String name;
    private boolean safetyReady;
}
