package com.campin.weather;

import java.time.LocalDate;

// 하루치 날씨 예보 (기상청 단기예보를 하루 단위로 줄인 것). 모르는 값은 null
//  - minTemp · maxTemp: 그날 최저 · 최고기온(℃)
//  - pop: 강수확률(%) 그날 가장 높은 값
//  - maxWind: 풍속(m/s) 그날 가장 센 값
//  - sky: 맑음 · 구름많음 · 흐림 (낮 12시 기준)
//  - precip: 없음 · 비 · 비/눈 · 눈 · 소나기 (그날 처음 나오는 강수 형태)
// 예보가 없는 날(범위 밖)은 none(date) — 값이 전부 null
public record DayForecast(LocalDate date, Double minTemp, Double maxTemp, Integer pop, Double maxWind,
                          String sky, String precip) {

    public static DayForecast none(LocalDate date) {
        return new DayForecast(date, null, null, null, null, null, null);
    }

    public boolean hasData() {
        return minTemp != null || maxTemp != null || pop != null || maxWind != null || sky != null;
    }
}
