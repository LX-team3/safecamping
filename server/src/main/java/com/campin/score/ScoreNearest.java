package com.campin.score;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 종류별 가장 가까운 시설까지 직선거리 (public.facilities). 거리 기준 종류(보안등·CCTV 등)의 점수에 쓴다
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScoreNearest {
    private String type;
    private double distM;
}
