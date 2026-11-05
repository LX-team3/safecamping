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

// 가족방 · 일정 공유 API. 회원 전용 (SecurityConfig 의 /api/family/**)
@RestController                           // 스프링: 돌려주는 값을 JSON 으로 바꿔 응답하는 컨트롤러
@RequestMapping("/api/family/schedules")  // 이 클래스의 모든 주소 앞에 /api/family/schedules 를 붙인다
@RequiredArgsConstructor                  // Lombok: final 필드를 받는 생성자를 만든다 → 스프링이 그 생성자로 주입
public class ScheduleController {

    private final ScheduleService scheduleService;

    // 일정 목록 (시간 순): GET /api/family/schedules
    @GetMapping
    public ApiResponse<List<ScheduleResponse>> list(Authentication auth) {
        return ApiResponse.ok(scheduleService.findAll(auth.getName()));
    }

    // 일정 추가: POST /api/family/schedules  {"title": "출발", "startAt": "2026-11-14T06:00:00+09:00", "memo": "..."}
    // @Valid: ScheduleRequest 의 검사(@NotBlank·@NotNull 등)를 실행한다 (틀리면 400)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)  // 스프링: 성공하면 201(새로 만듦)으로 답한다
    public ApiResponse<ScheduleResponse> add(Authentication auth, @Valid @RequestBody ScheduleRequest body) {
        return ApiResponse.ok(scheduleService.add(auth.getName(), body));
    }

    // 일정 삭제: DELETE /api/family/schedules/3
    // @PathVariable: 주소 안의 {scheduleId} 값을 받는다
    @DeleteMapping("/{scheduleId}")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable Long scheduleId) {
        scheduleService.delete(auth.getName(), scheduleId);
        return ApiResponse.ok(null);
    }
}
