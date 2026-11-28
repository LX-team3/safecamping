package com.campin.brief;

import java.time.LocalDate;
import java.util.List;

// 안전자료 조각 검색 조건 (AI 브리핑 6단계)
// 꼬리표로 "이 캠핑에 맞지 않는 조각"을 먼저 빼고, 남은 것 중 뜻이 가까운 순으로 고른다 (SafetyDocRepository.search)
// 왜 꼬리표로 먼저 거르나: 뜻 거리만 쓰면 아이 없는 가족에게 아이 전용 수칙이, 한겨울에 여름 수칙이 섞일 수 있다
//
// 꼬리표 값 (2026-11-28 벡터 DB 에서 셈, 조각 120개)
//   companion  all 114 · child 5 · pet 1
//   sleep_type all 103 · tent 16 · car 1
//   season     all 92 · winter 13 · summer 12 · spring 3 (autumn 없음)
//
// companions      = 남길 동행 꼬리표 (all 은 늘 남김)
// sleepTypes      = 남길 숙박 꼬리표 (all 은 늘 남김)
// excludedSeasons = 뺄 계절 꼬리표 (반대 계절만)
public record SafetyDocFilter(List<String> companions, List<String> sleepTypes, List<String> excludedSeasons) {

    // AI 에게 절대 넘기지 않는 조각 (2026-11-28 사용자 결정). 조각의 메모(note)를 보고 골랐다
    // 오타 · 옮겨 적기 메모만 있는 조각은 원문 대조 검수를 마쳐서 그대로 쓴다
    static final List<String> EXCLUDED_IDS = List.of(
            // 2016년 자료의 응급처치 — 메모 "최신 응급처치 지침과 대조 필요". 처치법이 틀리면 바로 다칠 수 있어 뺀다
            "JD-019",  // 응급처치 때 꼭 할 일
            "JD-020",  // 충격(쇼크) 증상과 처치
            "JD-021",  // 상처와 출혈
            "JD-023",  // 일사병 증상과 처치
            "JD-024",  // 음식물이 목에 걸렸을 때
            "JD-030",  // 겨울철 안전 ①: 동상 처치 수치(38~42도, 20~40분)
            "JD-033",  // 외상용 구급용품 ①: 포이즌 리무버 사용
            "JD-042",  // 산나물·독버섯
            "JD-044",  // 벌에 쏘이지 않으려면: 옷 색깔 설명
            "JD-048",  // 눈을 찔렸을 때
            "JD-058",  // 화상 응급처치: 화상 연고 등
            // 기사 인용 "가솔린 랜턴이나 스토브가 더 안전" — 메모 "검수 필요"
            "JD-004");

    // 조건 만들기
    //   withChild · withPet = 아이 · 반려동물과 함께 가는지 (아니면 아이 · 반려동물 전용 조각을 뺀다)
    //   sleepType = "tent" · "car" · null(모름 → 숙박으로 거르지 않음)
    //   checkIn   = 체크인 날짜 (null 이면 계절로 거르지 않음)
    public static SafetyDocFilter of(boolean withChild, boolean withPet, String sleepType, LocalDate checkIn) {
        List<String> companions = withChild && withPet ? List.of("all", "child", "pet")
                : withChild ? List.of("all", "child")
                : withPet ? List.of("all", "pet")
                : List.of("all");

        List<String> sleepTypes;
        if (sleepType == null) {
            sleepTypes = List.of("all", "tent", "car");
        } else if (sleepType.equals("tent") || sleepType.equals("car")) {
            sleepTypes = List.of("all", sleepType);
        } else {
            // 서버 코드끼리 부르는 곳이라 잘못 넘기면 바로 알 수 있게 멈춘다 (사용자 입력이 아님)
            throw new IllegalArgumentException("sleepType 은 tent · car · null 중 하나입니다: " + sleepType);
        }

        return new SafetyDocFilter(companions, sleepTypes, excludedSeasons(checkIn));
    }

    // 반대 계절만 뺀다 (2026-11-28 사용자 결정)
    //   12~2월(겨울) → 여름 조각을 뺌 / 6~8월(여름) → 겨울 조각을 뺌 / 봄 · 가을은 거르지 않음
    // 월 묶음은 체크리스트 계절(mycamping/ChecklistItems.seasonOf)과 같다
    // 왜 "그 계절만 남기기"가 아닌가: 11월 말 인제처럼 가을인데 이미 추운 날, 동상 · 난방 같은 겨울 조각이 빠지면 안 된다
    static List<String> excludedSeasons(LocalDate checkIn) {
        if (checkIn == null) return List.of();
        int month = checkIn.getMonthValue();
        if (month == 12 || month <= 2) return List.of("summer");
        if (month >= 6 && month <= 8) return List.of("winter");
        return List.of();
    }
}
