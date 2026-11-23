package com.campin.mycamping;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 출발 준비 체크리스트 API. /api/member/** 는 SecurityConfig 에서 로그인 필요 → authentication 은 null 이 아니다
@RestController
@RequestMapping("/api/member/camp-plans")
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService service;

    // 내가 등록한 캠핑 하나의 출발 준비: GET /api/member/camp-plans/3/checklist
    // 응답 필드는 ChecklistResponse 참고. 같은 가족방 사람의 일정도 볼 수 있다. 그 밖의 남의 일정 id 는 404
    @GetMapping("/{planId}/checklist")
    public ApiResponse<ChecklistResponse> checklist(Authentication authentication, @PathVariable Long planId) {
        return ApiResponse.ok(service.find(authentication.getName(), planId));
    }
}
