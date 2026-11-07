package com.campin.common;

// 차로 걸리는 시간을 화면 색으로 나눈다 (안전점수 탭 · 시설 상세 · 경로 지도가 같이 쓴다)
// 스크린샷(5·9분 초록, 17·22분 주황)에 맞춘 임시 기준 — 안전 기준이 정해지면 바꾼다
public final class DriveTone {

    private static final int SAFE_MAX_MIN = 10;

    private DriveTone() {
    }

    public static String of(int driveMin) {
        return driveMin <= SAFE_MAX_MIN ? "safe" : "caution";
    }
}
