package com.campin.disaster;

import java.util.List;
import java.util.Optional;

// 그 위치에 최근 몇 시간 동안 내린 비를 주는 곳
// 실제로는 KmaRainClient(기상청 초단기실황). 인터페이스로 둔 이유: 테스트에서 기상청을 부르지 않고 가짜 결과를 넣기 위해
public interface RainSource {

    // 가장 최근 시각부터 거꾸로 hours 개 (0번 = 가장 최근). 중간에 못 받은 시간은 mm 가 null
    // 키가 없거나 가장 최근 시간부터 못 받으면 Optional.empty() — 예시 값을 대신 만들어 쓰지 않는다
    Optional<List<HourRain>> recentHours(double lat, double lng, int hours);
}
