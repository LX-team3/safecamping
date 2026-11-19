package com.campin.weather;

import java.util.List;
import java.util.Optional;

// 그 위치의 날씨 예보를 주는 곳
// 실제로는 KmaForecastClient(기상청 단기예보). 인터페이스로 둔 이유: 테스트에서 기상청을 부르지 않고 가짜 결과를 넣기 위해
public interface ForecastSource {

    // 실제로 받았으면 날짜 순 하루치 목록 (기상청이 주는 날만 들어 있고, 없는 날은 빠져 있다).
    // 키가 없거나 호출에 실패하면 Optional.empty() — 예시 값을 대신 만들어 쓰지 않는다
    Optional<List<DayForecast>> daily(double lat, double lng);
}
