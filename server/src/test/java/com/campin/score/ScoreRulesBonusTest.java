package com.campin.score;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// 진단 유형 가산점(ScoreRules.erBonus): 응급실 30분 안 5점 → 60분 넘으면 0점, 사이는 비례 · 반올림
class ScoreRulesBonusTest {

    @Test
    void 응급실이_30분_안이면_5점() {
        assertEquals(5, ScoreRules.erBonus(11));
        assertEquals(5, ScoreRules.erBonus(30));
    }

    @Test
    void 사이는_시간에_비례해_반올림() {
        assertEquals(3, ScoreRules.erBonus(45));  // 5 × 15/30 = 2.5 → 3
        assertEquals(2, ScoreRules.erBonus(51));  // 5 × 9/30 = 1.5 → 2
        assertEquals(1, ScoreRules.erBonus(56));  // 5 × 4/30 = 0.67 → 1
    }

    @Test
    void 응급실이_60분_넘거나_정보가_없으면_0점() {
        assertEquals(0, ScoreRules.erBonus(60));
        assertEquals(0, ScoreRules.erBonus(78));
        assertEquals(0, ScoreRules.erBonus(null));
    }
}
