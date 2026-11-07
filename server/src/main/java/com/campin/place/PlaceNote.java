package com.campin.place;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 상세 화면 안내 상자 하나 (place_note). level: danger(위험할 땐) / caution(주의) / safe(안전)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlaceNote {
    private String level;
    private String body;
}
