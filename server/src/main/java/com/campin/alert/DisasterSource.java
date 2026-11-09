package com.campin.alert;

import java.util.List;
import java.util.Optional;

// 최근 재난문자를 주는 곳
// 실제로는 SafetyDataDisasterClient(행정안전부 긴급재난문자). 인터페이스로 둔 이유: 테스트에서 실제 API 를 부르지 않기 위해
public interface DisasterSource {

    // 실제 데이터를 받았으면 그 목록(없으면 빈 목록), 키가 없거나 호출에 실패하면 Optional.empty()
    // → empty 이면 AlertService 가 더미를 대신 쓰고 화면에 "예시"라고 표시한다
    Optional<List<Alert>> recentMessages();
}
