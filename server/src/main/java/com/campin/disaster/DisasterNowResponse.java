package com.campin.disaster;

import java.time.LocalDateTime;
import java.util.List;

// 재난지도 "지금" 정보 응답 (인북천 캠핑장)
// rainAvailable: 기상청 실황을 받았으면 true. false 면 rain*·level 은 null → 화면은 시뮬레이션(슬라이더)만 보여 준다
// observedAt: 가장 최근 관측 시각 (정시, 한국 시간)
// rain3h·rain12h·rain24h: 최근 3·12·24시간 동안 내린 비(mm). 그 사이 한 시간이라도 못 받았으면 null (모자란 값을 0 으로 채우지 않는다)
// level: 0 안전 · 1 준비 · 2 대피 · 3 침수, 화면 이름은 '대피' (DisasterRules). reasons: 그 단계가 된 까닭
// warningAvailable: 기상특보를 실제로 받았으면 true. false 면 warnings 는 빈 목록 (예시 특보를 넣지 않는다)
// warnings: 인제군에 지금 발효 중인 기상특보
// water: 상류 반월촌교 수위(한강홍수통제소). 못 받았으면 null. level 은 비 단계와 수위 단계 중 높은 쪽
public record DisasterNowResponse(String place, boolean rainAvailable, LocalDateTime observedAt,
                                  Double rain3h, Double rain12h, Double rain24h,
                                  Integer level, List<String> reasons,
                                  boolean warningAvailable, List<Warning> warnings,
                                  Water water) {

    // 수위(m, 수위표 눈금) · 기준수위(없으면 null) · 수위만으로 본 단계(오래된 자료면 null) · stale: 1시간 넘게 새 자료가 없음
    public record Water(String station, LocalDateTime observedAt, Double wl,
                        Double attention, Double alarm, Double serious, Integer level, boolean stale) {
    }

    // 특보 한 건. category 예: 호우주의보 · area 예: 강원 인제평지, 양구평지 · issuedAt: 발표 시각(ISO)
    public record Warning(String category, String area, String message, String issuedAt) {
    }
}
