package com.campin.mycamping;

// 출발 준비 체크리스트 항목 하나
// id: 고정값 (체크 상태를 브라우저에 저장할 때 일정 번호와 함께 키로 씀 → 문구를 고쳐도 체크가 안 풀리게 id 는 바꾸지 않는다)
// group: 특보 · 차박 · 계절 · 공통 (화면이 이 순서로 접고 펼 수 있게 묶음)
// why: 항목 아래 작은 글씨 (특보 이름 · 계절 이유). 없으면 null
// level: warn(특보 대응, 화면에서 눈에 띄게) · normal
public record ChecklistItem(String id, String group, String text, String why, String level) {

    static ChecklistItem of(String id, String group, String text, String why) {
        return new ChecklistItem(id, group, text, why, "특보".equals(group) ? "warn" : "normal");
    }
}
