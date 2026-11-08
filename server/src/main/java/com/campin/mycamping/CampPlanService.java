package com.campin.mycamping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;

import lombok.RequiredArgsConstructor;

// 내 캠핑 (캠핑장 상세 → "내 캠핑에 일정 등록" → 내 캠핑 탭 카드). 회원 개인 것이라 가족방과 상관없다
// 프론트에서도 같은 검사를 하지만 서버에서 한 번 더 한다
@Service
@RequiredArgsConstructor
public class CampPlanService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");  // "오늘"은 한국 날짜 기준

    private final MemberMapper memberMapper;
    private final CampPlanMapper campPlanMapper;

    public List<CampPlanResponse> findAll(String loginId) {
        Long memberId = findMember(loginId).getId();
        return campPlanMapper.findByMemberId(memberId).stream().map(CampPlanResponse::from).toList();
    }

    public CampPlanResponse add(String loginId, CampPlanRequest req) {
        Long memberId = findMember(loginId).getId();

        if (req.checkIn().isBefore(LocalDate.now(KST))) {
            throw new InvalidRequestException("지난 날짜는 고를 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(req.checkIn(), req.checkOut()) < 1) {
            throw new InvalidRequestException("체크아웃은 체크인 다음 날부터 고를 수 있습니다.");
        }
        if (campPlanMapper.exists(memberId, req.campId(), req.checkIn())) {
            throw new InvalidRequestException("이미 같은 날짜로 등록한 캠핑입니다.");
        }

        CampPlan plan = new CampPlan();
        plan.setMemberId(memberId);
        plan.setCampId(req.campId());
        plan.setCampName(req.campName().trim());
        plan.setPlace(blankToNull(req.place()));
        plan.setCheckIn(req.checkIn());
        plan.setCheckOut(req.checkOut());
        plan.setMemo(blankToNull(req.memo()));
        campPlanMapper.insert(plan);
        return CampPlanResponse.from(plan);
    }

    public void delete(String loginId, Long planId) {
        Long memberId = findMember(loginId).getId();
        // 남의 등록이거나 없는 id 면 0줄 → 없는 것처럼 404
        if (campPlanMapper.delete(memberId, planId) == 0) {
            throw new NotFoundException("등록한 캠핑을 찾을 수 없습니다.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Member findMember(String loginId) {
        Member m = memberMapper.findByLoginId(loginId);
        if (m == null) {
            throw new NotFoundException("회원 정보를 찾을 수 없습니다.");
        }
        return m;
    }
}
