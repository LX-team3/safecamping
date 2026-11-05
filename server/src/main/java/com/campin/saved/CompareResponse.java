package com.campin.saved;

import java.util.List;

// 관심 캠핑장 비교 화면. 표의 한 줄(row)마다 캠핑장 수만큼 칸(cell)이 있다
// dummy: 값이 더미이면 true → 화면에 "더미 데이터" 표시
// totalCount: 목록에 담긴 전체 수 (비교는 앞의 3곳만 보여 준다)
public record CompareResponse(boolean dummy, Long listId, String listName, int totalCount,
                              List<Camp> camps, List<Row> rows) {

    public record Camp(String id, String name) {
    }

    // kind: "score" 면 안전점수 줄(초록 글씨), "normal" 이면 보통 줄
    public record Row(String label, String kind, List<Cell> cells) {
    }

    // best: 이 줄에서 가장 좋은 값이면 true → 진하게 보여 준다
    public record Cell(String text, boolean best) {
    }
}
