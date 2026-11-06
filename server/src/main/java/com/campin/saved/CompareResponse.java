package com.campin.saved;

import java.util.List;

// 관심 캠핑장 비교 화면. 항목(row)마다 캠핑장 수만큼 칸(cell)이 있다 → 화면은 항목별 카드에 캠핑장별 막대로 그린다
// dummy: 값이 더미이면 true → 화면에 "더미 데이터" 표시
// totalCount: 목록에 담긴 전체 수 (비교는 앞의 3곳만 보여 준다)
public record CompareResponse(boolean dummy, Long listId, String listName, int totalCount,
                              List<Camp> camps, List<Row> rows) {

    public record Camp(String id, String name) {
    }

    // kind: "score"(안전점수, 막대는 100점 기준) · "minutes"(시간, 막대는 가장 긴 값 기준) · "yesno"(가능/불가, 막대 없음)
    // bestLabel: 가장 좋은 칸 옆에 붙일 칩 문구 (예: 가장 가까움)
    public record Row(String label, String kind, String bestLabel, List<Cell> cells) {
    }

    // value: 막대 길이에 쓸 숫자 (점수·분). 막대를 그리지 않는 칸은 null
    // best: 이 항목에서 가장 좋은 값이면 true → 칩을 붙이고 진하게 보여 준다
    public record Cell(String text, Integer value, boolean best) {
    }
}
