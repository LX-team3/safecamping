package com.campin.family;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

// 가족방 위치 공유 (가족방 3차). "내가 들어가 있는 방" 구성원의 마지막 위치만 보여 준다
// 위치 공유에 동의한 사람만 family_location 에 줄이 있다 → 줄이 있는 것 = 동의한 것 (동의 여부를 따로 저장하지 않음)
@Service                   // 스프링: 비즈니스 로직 담당 빈으로 등록
@RequiredArgsConstructor   // Lombok: final 필드를 받는 생성자를 만든다 → 스프링이 그 생성자로 주입
public class LocationService {

    private final LocationMapper locationMapper;
    private final FamilyService familyService;

    public List<LocationResponse> findAll(String loginId) {
        // 방 번호를 주소로 받지 않고 로그인한 회원의 방을 찾는다 → 남의 방 위치에 닿을 길이 없다 (방이 없으면 404)
        MyRoom my = familyService.requireMyRoom(loginId);
        return locationMapper.findAll(my.roomId()).stream()
                .map(l -> LocationResponse.from(l, my.memberId()))
                .toList();
    }

    // 내 위치 저장 (동의하기 · 새로고침). 가족방이 없으면 보여 줄 사람이 없으므로 저장하지 않는다 (404)
    public void saveMine(String loginId, LocationRequest body) {
        MyRoom my = familyService.requireMyRoom(loginId);
        locationMapper.upsert(my.memberId(), body.lng(), body.lat());  // 화면은 lng, DB 는 lon
    }

    // 위치 공유 끄기
    public void deleteMine(String loginId) {
        MyRoom my = familyService.requireMyRoom(loginId);
        locationMapper.delete(my.memberId());
    }
}
