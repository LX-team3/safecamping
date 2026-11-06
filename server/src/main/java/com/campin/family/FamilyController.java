package com.campin.family;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 내 캠핑 탭 · 가족 모임방 API. 회원 전용 (SecurityConfig 에서 /api/family/** 로그인 필요)
// 방 번호를 주소에 넣지 않는다 — 서비스가 로그인한 회원의 방을 찾아 쓴다 (한 사람당 방 하나)
// Authentication: 스프링 시큐리티가 넣어 주는 로그인 정보. getName() 은 로그인 아이디
@RestController                 // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/family")  // 이 클래스의 모든 주소 앞에 /api/family 를 붙인다
@RequiredArgsConstructor        // Lombok: final 필드를 받는 생성자를 만든다 → 스프링이 그 생성자로 주입
public class FamilyController {

    private final FamilyService familyService;

    // 내 가족방: GET /api/family/room → 없으면 data 가 null
    @GetMapping("/room")
    public ApiResponse<FamilyRoomResponse> room(Authentication auth) {
        return ApiResponse.ok(familyService.findMyRoom(auth.getName()));
    }

    // 가족방 만들기: POST /api/family/room  {"name": "우리 가족"}
    // @Valid: FamilyRoomRequest 의 @NotBlank·@Size 검사를 실행한다 (틀리면 GlobalExceptionHandler 가 400)
    @PostMapping("/room")
    @ResponseStatus(HttpStatus.CREATED)  // 스프링: 성공하면 200 대신 201(새로 만듦)으로 답한다
    public ApiResponse<FamilyRoomResponse> create(Authentication auth, @Valid @RequestBody FamilyRoomRequest body) {
        return ApiResponse.ok(familyService.createRoom(auth.getName(), body.name()));
    }

    // 초대코드로 들어가기: POST /api/family/room/join  {"inviteCode": "AB3K7Q"}
    @PostMapping("/room/join")
    public ApiResponse<FamilyRoomResponse> join(Authentication auth, @Valid @RequestBody FamilyJoinRequest body) {
        return ApiResponse.ok(familyService.join(auth.getName(), body.inviteCode()));
    }

    // 나가기: DELETE /api/family/room/me (마지막 사람이면 방도 지운다)
    @DeleteMapping("/room/me")
    public ApiResponse<Void> leave(Authentication auth) {
        familyService.leave(auth.getName());
        return ApiResponse.ok(null);
    }

    // 모일 장소 목록: GET /api/family/places
    @GetMapping("/places")
    public ApiResponse<List<MeetingPlaceResponse>> places(Authentication auth) {
        return ApiResponse.ok(familyService.findPlaces(auth.getName()));
    }

    // 모일 장소 추가: POST /api/family/places  {"name": "...", "description": "...", "lon": 127.1, "lat": 37.5}
    @PostMapping("/places")
    @ResponseStatus(HttpStatus.CREATED)  // 스프링: 성공하면 201(새로 만듦)으로 답한다
    public ApiResponse<MeetingPlaceResponse> addPlace(Authentication auth, @Valid @RequestBody MeetingPlaceRequest body) {
        return ApiResponse.ok(familyService.addPlace(auth.getName(), body));
    }

    // 모일 장소 삭제: DELETE /api/family/places/3
    // @PathVariable: 주소 안의 {placeId} 값을 받는다
    @DeleteMapping("/places/{placeId}")
    public ApiResponse<Void> deletePlace(Authentication auth, @PathVariable Long placeId) {
        familyService.deletePlace(auth.getName(), placeId);
        return ApiResponse.ok(null);
    }
}
