package com.campin.route;

import java.util.List;
import java.util.Optional;

// 자동차 길찾기 결과를 주는 곳
// 실제로는 KakaoDirectionsClient(카카오모빌리티). 인터페이스로 둔 이유: 테스트에서 카카오를 부르지 않고 가짜 결과를 넣기 위해
public interface DirectionsSource {

    // 카카오가 답을 줬으면 그 결과 (길을 못 찾았어도 resultCode 로 알려 준다)
    // 키가 없거나 호출에 실패하면 Optional.empty() → RouteService 가 직선으로 대신 그린다
    Optional<Result> find(double fromLat, double fromLng, double toLat, double toLng);

    // resultCode: 0 이면 성공. distanceM: 미터, durationS: 초
    // vertexes: 카카오 형식 그대로 [경도, 위도, 경도, 위도, ...] (위도·경도 순서로 바꾸는 일은 RouteService 가 한다)
    record Result(int resultCode, String resultMsg, double distanceM, double durationS, List<Double> vertexes) {
    }
}
