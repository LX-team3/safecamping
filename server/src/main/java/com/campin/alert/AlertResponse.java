package com.campin.alert;

import java.util.List;

// 알림 화면 응답
// warningDummy: 기상특보가 예시(더미)면 true → 키가 없거나 기상청 호출에 실패한 경우
// disasterDummy: 재난문자가 예시(더미)면 true → 재난문자 API 를 붙이기 전까지 늘 true
//   화면은 true 인 쪽에만 "예시" 표시를 붙인다 (안전 정보를 진짜처럼 보이게 하지 않기 위해)
// sido: 보여 주는 지역 (null 이면 전국). regions: 지역을 직접 고를 때 쓸 시·도 목록
// activeWarnings: 그 지역에 지금 발효 중인 기상특보. items: 특보·재난문자를 최신순으로 섞은 목록
// todayDisasterCount: 그 지역의 오늘 재난문자 수 (한국 시간 기준)
public record AlertResponse(boolean warningDummy, boolean disasterDummy, String sido, List<String> regions,
                            List<Alert> activeWarnings, int todayDisasterCount, List<Alert> items) {
}
