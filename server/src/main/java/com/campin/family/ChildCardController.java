package com.campin.family;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 가족방 · 아이 건강 응급 카드 API. 회원 전용 (SecurityConfig 의 /api/family/**)
@RestController                          // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/family/children")  // 이 클래스의 모든 주소 앞에 /api/family/children 을 붙인다
@RequiredArgsConstructor                 // Lombok: final 필드를 받는 생성자를 만든다 → 스프링이 그 생성자로 주입
public class ChildCardController {

    private final ChildCardService childCardService;

    // 카드 목록: GET /api/family/children
    @GetMapping
    public ApiResponse<List<ChildCardResponse>> list(Authentication auth) {
        return ApiResponse.ok(childCardService.findAll(auth.getName()));
    }

    // 카드 추가: POST /api/family/children  {"name": "서준", "birthDate": "2019-05-02", "allergy": "땅콩", ...}
    // @Valid: ChildCardRequest 의 검사(@NotBlank·@Size 등)를 실행한다 (틀리면 400)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)  // 스프링: 성공하면 201(새로 만듦)으로 답한다
    public ApiResponse<ChildCardResponse> add(Authentication auth, @Valid @RequestBody ChildCardRequest body) {
        return ApiResponse.ok(childCardService.add(auth.getName(), body));
    }

    // 카드 수정: PUT /api/family/children/3 (모든 칸을 새 값으로 바꾼다)
    // @PathVariable: 주소 안의 {cardId} 값을 받는다
    @PutMapping("/{cardId}")
    public ApiResponse<ChildCardResponse> update(Authentication auth, @PathVariable Long cardId,
                                                 @Valid @RequestBody ChildCardRequest body) {
        return ApiResponse.ok(childCardService.update(auth.getName(), cardId, body));
    }

    // 카드 삭제: DELETE /api/family/children/3
    @DeleteMapping("/{cardId}")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable Long cardId) {
        childCardService.delete(auth.getName(), cardId);
        return ApiResponse.ok(null);
    }
}
