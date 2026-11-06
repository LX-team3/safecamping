package com.campin.alert;

import java.time.OffsetDateTime;

// 알림 한 건 (기상특보 또는 재난문자)
// kind: "warning"(기상특보) · "disaster"(재난문자)
// category: 칩에 쓸 짧은 이름 (예: 호우주의보, 한파, 산불)
// active: 기상특보가 지금 발효 중이면 true (재난문자는 항상 false)
public record Alert(String id, String kind, String sido, String area, String category,
                    String message, OffsetDateTime issuedAt, boolean active) {
}
