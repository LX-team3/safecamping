package com.campin.mycamping;

import java.util.List;
import java.util.Optional;

import com.campin.weather.DayForecast;

// 체크리스트를 만들 때 바깥에서 가져오는 것 (캠핑장 정보 · 차박 안전점수 · 기상특보)
// 인터페이스로 둔 이유: 테스트에서 DB·기상청 없이 가짜 값을 넣기 위해 (alert/WarningSource 와 같은 방식)
public interface ChecklistSource {

    // 일정의 대상 한 곳: 위치(특보 지역을 찾는 데 씀) + 안전 정보
    record Target(double lat, double lng, ChecklistResponse.Safety safety) {
    }

    // 등록 캠핑장. 캠핑장이 없어졌으면(지워짐) empty
    Optional<Target> ofCamp(Long campId);

    // 캠핑장이 아닌 자리(차박 후보지·찍은 자리): 찍은 자리의 안전점수 (캠핑장과 같은 계산, 응급실 분은 어림)
    Target ofSpot(double lat, double lng);

    // 그 위치 시·도에 지금 발효 중인 기상특보. 실제로 받았으면 그 목록(없으면 빈 목록),
    // 못 받았거나(키 없음·호출 실패) 예시(더미)뿐이면 empty
    Optional<List<ChecklistResponse.Warning>> warnings(double lat, double lng);

    // 그 위치의 날씨 예보(단기예보). 받았으면 기상청이 주는 날만 담긴 목록, 못 받았으면(키 없음·호출 실패) empty
    Optional<List<DayForecast>> forecast(double lat, double lng);
}
