package com.campin.weather;

// 위도·경도 → 기상청 동네예보 격자(nx, ny) 변환
// 단기예보는 위·경도가 아니라 5km 격자 번호로 조회한다. 변환식은 기상청이 공개한 람베르트 정각원추도법 값 그대로이고
// 숫자(RE, SLAT1 …)는 기상청 기준이라 임의로 바꾸지 않는다
public final class KmaGrid {

    private static final double RE = 6371.00877;  // 지구 반경(km)
    private static final double GRID = 5.0;       // 격자 간격(km)
    private static final double SLAT1 = 30.0;     // 표준 위도 1
    private static final double SLAT2 = 60.0;     // 표준 위도 2
    private static final double OLON = 126.0;     // 기준점 경도
    private static final double OLAT = 38.0;      // 기준점 위도
    private static final double XO = 43;          // 기준점 X 격자
    private static final double YO = 136;         // 기준점 Y 격자

    private KmaGrid() {
    }

    public record Point(int nx, int ny) {
    }

    public static Point of(double lat, double lon) {
        double degrad = Math.PI / 180.0;
        double re = RE / GRID;
        double slat1 = SLAT1 * degrad;
        double slat2 = SLAT2 * degrad;
        double olon = OLON * degrad;
        double olat = OLAT * degrad;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + lat * degrad * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = lon * degrad - olon;
        if (theta > Math.PI) theta -= 2.0 * Math.PI;
        if (theta < -Math.PI) theta += 2.0 * Math.PI;
        theta *= sn;

        int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int ny = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        return new Point(nx, ny);
    }
}
