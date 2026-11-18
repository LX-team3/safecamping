package com.campin.mycamping;

import java.time.LocalDate;
import java.util.List;

// 출발 준비 체크리스트 응답 (GET /api/member/camp-plans/{planId}/checklist)
//  - season: 체크인 월 기준 (봄 3~5 · 여름 6~8 · 가을 9~11 · 겨울 12~2)
//  - past: 이미 끝난 캠핑 (체크아웃이 어제 이전)
//  - near: 출발 3일 전부터 캠핑이 끝날 때까지 (캠핑 중 포함) → 이때만 특보를 확인해서 항목에 반영한다
//  - openDate: 특보·예보가 보이기 시작하는 날 (체크인 3일 전). 화면이 "○월 ○일부터 보여요"에 쓴다
//  - warningsAvailable: 특보를 실제로 받았는지. false 면(먼 일정 · 기상청 키 없음 · 호출 실패) warnings 는 비어 있고,
//    예시(더미) 특보를 진짜처럼 항목으로 만들지 않기 위해 화면은 "특보 정보를 받지 못했어요"만 보여 준다
//  - safety: 이 곳의 안전 정보 (캠핑장은 camp 표 값, 차박 자리는 찍은 자리의 안전점수 계산). 모르면 null
public record ChecklistResponse(Plan plan, String season, boolean past, boolean near, LocalDate openDate,
                                boolean warningsAvailable, List<Warning> warnings, Safety safety,
                                List<ChecklistItem> items) {

    // campId 가 null 이면 캠핑장이 아닌 차박 자리 (lat·lng 가 있음)
    public record Plan(Long id, Long campId, String name, Double lat, Double lng,
                       LocalDate checkIn, LocalDate checkOut) {
    }

    // 지금 발효 중인 기상특보 (기상청 특보 현황 그대로, 시·도 단위)
    public record Warning(String category, String area, String message) {
    }

    // erApprox: 응급실 시간이 직선거리 어림값인지 (차박 자리 true → 화면에 "약")
    public record Safety(Integer score, String grade, Integer erDriveMin, boolean erApprox) {
    }
}
