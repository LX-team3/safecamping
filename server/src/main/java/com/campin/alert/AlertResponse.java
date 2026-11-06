package com.campin.alert;

import java.util.List;

// 알림 화면 응답
// dummy: 더미 데이터면 true → 화면에 "더미 데이터" 표시 (API 키를 받으면 실제 데이터로 바꾼다)
// sido: 보여 주는 지역 (null 이면 전국). regions: 지역을 직접 고를 때 쓸 시·도 목록
// activeWarnings: 그 지역에 지금 발효 중인 기상특보. items: 특보·재난문자를 최신순으로 섞은 목록
// todayDisasterCount: 그 지역의 오늘 재난문자 수 (한국 시간 기준)
public record AlertResponse(boolean dummy, String sido, List<String> regions,
                            List<Alert> activeWarnings, int todayDisasterCount, List<Alert> items) {
}
