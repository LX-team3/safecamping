package com.campin.alert;

import java.util.List;
import java.util.Optional;

// 지금 발효 중인 기상특보를 주는 곳
// 실제로는 KmaWarningClient(기상청). 인터페이스로 둔 이유: 테스트에서 기상청을 부르지 않고 가짜 결과를 넣기 위해
public interface WarningSource {

    // 실제 데이터를 받았으면 그 목록(특보가 없으면 빈 목록), 키가 없거나 기상청 호출에 실패하면 Optional.empty()
    // → empty 이면 AlertService 가 더미를 대신 쓰고 화면에 "예시"라고 표시한다
    Optional<List<Alert>> activeWarnings();
}
