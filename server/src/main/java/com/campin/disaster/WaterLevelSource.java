package com.campin.disaster;

import java.util.Optional;

// 캠핑장 상류 수위를 어디서 받아 오는지. 실제는 HrfcoWaterClient, 테스트에서는 가짜를 넣는다
public interface WaterLevelSource {

    // 지금 수위 + 기준수위. 키가 없거나 받지 못했으면 empty
    Optional<WaterLevel> current();
}
