package com.campin.facility;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// facility 테이블 한 줄 (안전 시설)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Facility {
    private Long id;
    private String type;      // er, fire, police, store, toilet
    private String name;
    private String sido;
    private String sigungu;
    private String eupmyeon;
    private String address;   // 없으면 null → 화면 "확인 필요"
    private String phone;     // 없으면 null → 복사 버튼 숨김
    private String hours;     // 운영 시간. 공공데이터 연동 전에는 null
    private double lat;
    private double lng;
    private boolean dummy;
}
