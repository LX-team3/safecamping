package com.campin.score;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 종류별 가중치와 거리 기준 (public.score_weights, 읽기만 한다)
// ScoreRules.TIME 에 있는 종류는 fullDistM·zeroDistM 대신 분 기준을 쓴다
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScoreWeight {
    private String type;
    private double weight;
    private double fullDistM;
    private double zeroDistM;
}
