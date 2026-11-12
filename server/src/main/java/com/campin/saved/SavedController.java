package com.campin.saved;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 저장 화면(목록 탭) · 관심 캠핑장 비교 API. 회원 전용 (SecurityConfig 에서 /api/saved/** 로그인 필요)
// Authentication: 스프링 시큐리티가 넣어 주는 로그인 정보. getName() 은 로그인 아이디
@RestController                 // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/saved")   // 이 클래스의 모든 주소 앞에 /api/saved 를 붙인다
@RequiredArgsConstructor
public class SavedController {

    private final SavedService savedService;

    // 내 목록들: GET /api/saved/lists (관심 캠핑장 기본 목록이 없으면 만들어서 돌려준다)
    @GetMapping("/lists")
    public ApiResponse<List<SavedListResponse>> lists(Authentication auth) {
        return ApiResponse.ok(savedService.findLists(auth.getName()));
    }

    // 새 목록: POST /api/saved/lists  {"name": "11월 가족 캠핑 후보"}
    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SavedListResponse> create(Authentication auth, @Valid @RequestBody SavedListRequest body) {
        return ApiResponse.ok(savedService.createList(auth.getName(), body.name()));
    }

    // 이름 바꾸기: PATCH /api/saved/lists/3  {"name": "..."}
    // @PathVariable: 주소 안의 {listId} 값을 받는다
    @PatchMapping("/lists/{listId}")
    public ApiResponse<Void> rename(Authentication auth, @PathVariable Long listId,
                                    @Valid @RequestBody SavedListRequest body) {
        savedService.renameList(auth.getName(), listId, body.name());
        return ApiResponse.ok(null);
    }

    // 목록 삭제: DELETE /api/saved/lists/3 (기본 목록은 400)
    @DeleteMapping("/lists/{listId}")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable Long listId) {
        savedService.deleteList(auth.getName(), listId);
        return ApiResponse.ok(null);
    }

    // 하트 상태: GET /api/saved/favorites → {"listId": 3, "campIds": [100568, ...]} ('관심 캠핑장' 기본 목록)
    @GetMapping("/favorites")
    public ApiResponse<FavoritesResponse> favorites(Authentication auth) {
        return ApiResponse.ok(savedService.favorites(auth.getName()));
    }

    // 캠핑장 담기: POST /api/saved/lists/3/camps/100568 (이미 담겨 있어도 성공)
    @PostMapping("/lists/{listId}/camps/{campId}")
    public ApiResponse<Void> addCamp(Authentication auth, @PathVariable Long listId, @PathVariable Long campId) {
        savedService.addCamp(auth.getName(), listId, campId);
        return ApiResponse.ok(null);
    }

    // 캠핑장 빼기: DELETE /api/saved/lists/3/camps/100568
    @DeleteMapping("/lists/{listId}/camps/{campId}")
    public ApiResponse<Void> removeCamp(Authentication auth, @PathVariable Long listId, @PathVariable Long campId) {
        savedService.removeCamp(auth.getName(), listId, campId);
        return ApiResponse.ok(null);
    }

    // 비교 표: GET /api/saved/lists/3/compare (앞의 3곳, 값은 DB 의 실제 데이터)
    @GetMapping("/lists/{listId}/compare")
    public ApiResponse<CompareResponse> compare(Authentication auth, @PathVariable Long listId) {
        return ApiResponse.ok(savedService.compare(auth.getName(), listId));
    }
}
