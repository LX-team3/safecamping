package com.campin.brief;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// AI 안전 브리핑 API (11단계). /api/member/** 는 SecurityConfig 에서 로그인 필요 → authentication 은 null 이 아니다
@RestController
@RequestMapping("/api/member/camp-plans")
@RequiredArgsConstructor
public class BriefController {

    private final BriefService service;

    // 내 캠핑 하나의 AI 대비 행동: POST /api/member/camp-plans/3/brief  본문 { "survey": {...} | null } (BriefRequest)
    // 왜 POST 인가: 브라우저에만 있는 진단 결과를 본문으로 받아야 하고, 부를 때마다 Claude 를 불러 비용이 든다 (주소만으로 반복 호출되지 않게)
    // 응답 BriefResult: ai=true 면 lines(대비 행동 + 근거), ai=false 면 화면이 지금의 문장 틀 브리핑을 보여 준다
    // 내 일정 · 같은 가족방 일정만 (그 밖 404), 끝난 캠핑 400. 저장하지 않고 부를 때마다 새로 만든다
    @PostMapping("/{planId}/brief")
    public ApiResponse<BriefResult> brief(Authentication authentication, @PathVariable Long planId,
                                          @RequestBody(required = false) BriefRequest req) {
        // required = false: 진단이 없는 화면이 본문 없이 보내도 된다
        BriefSurvey survey = req == null ? null : req.survey();
        return ApiResponse.ok(service.make(authentication.getName(), planId, survey));
    }
}
