package com.campin.brief;

import org.springframework.stereotype.Service;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;
import com.campin.mycamping.CampPlan;
import com.campin.mycamping.CampPlanMapper;
import com.campin.mycamping.ChecklistResponse;
import com.campin.mycamping.ChecklistService;

import lombok.RequiredArgsConstructor;

// AI 브리핑 7단계: 이 캠핑의 상황을 모은다 → BriefContext
// 특보 · 예보 · 안전점수는 출발 준비 체크리스트(ChecklistService)를 그대로 쓴다
//   → 같은 시기 규칙(출발 3일 전부터), 같은 캐시, 같은 "못 받으면 비워 둠" 규칙. 브리핑과 체크리스트가 서로 다른 날씨를 말하지 않는다
@Service
@RequiredArgsConstructor
public class BriefContextService {

    private final ChecklistService checklistService;
    private final MemberMapper memberMapper;
    private final CampPlanMapper campPlanMapper;

    // survey = 화면이 보낸 진단 요약 (진단을 안 했으면 null)
    // 내 일정이거나 같은 가족방 사람의 일정만 (체크리스트와 같은 범위). 그 밖은 404
    public BriefContext collect(String loginId, Long planId, BriefSurvey survey) {
        ChecklistResponse checklist = checklistService.find(loginId, planId);  // 회원 · 일정이 없으면 여기서 404
        // 끝난 캠핑은 만들지 않는다: 출발 전 대비 행동이라 뜻이 없고, 프롬프트의 "아직 없음(○월 ○일부터 확인)"도 맞지 않는다
        if (checklist.past()) {
            throw new InvalidRequestException("끝난 캠핑은 브리핑을 만들지 않습니다.");
        }

        // 동행이 적힌 메모는 체크리스트 응답에 없어서 일정을 한 번 더 읽는다 (같은 조회 범위 findVisible)
        Member member = memberMapper.findByLoginId(loginId);
        CampPlan plan = member == null ? null : campPlanMapper.findVisible(member.getId(), planId);
        if (plan == null) {
            throw new NotFoundException("등록한 캠핑을 찾을 수 없습니다.");
        }
        return BriefContext.of(plan, checklist, survey);
    }
}
