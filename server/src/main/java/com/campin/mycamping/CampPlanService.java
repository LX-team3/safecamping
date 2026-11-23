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

// 내 캠핑 (캠핑장 상세 · 차박 판정 카드 → "내 캠핑에 일정 등록" → 내 캠핑 탭 카드). 회원 개인 것이라 가족방과 상관없다
// 등록 대상: 등록 캠핑장(campId) 또는 캠핑장이 아닌 자리(차박 후보지·찍은 자리, lat·lng)
// 프론트에서도 같은 검사를 하지만 서버에서 한 번 더 한다
@Service
@RequiredArgsConstructor
public class CampPlanService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");  // "오늘"은 한국 날짜 기준

    private final MemberMapper memberMapper;
    private final CampPlanMapper campPlanMapper;
    private final CampReviewMapper reviewMapper;   // 일정을 지울 때 후기 사진 파일도 지우려고
    private final ReviewPhotoStore photoStore;

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

        // 등록 캠핑장(campId) 또는 캠핑장이 아닌 자리(lat·lng) 중 하나
        // 소수 6자리(약 10cm)로 맞춘다 → 같은 자리가 부동소수점 끝자리 때문에 다른 자리로 저장되지 않게
        Double lat = null;
        Double lng = null;
        if (req.campId() != null) {
            if (campPlanMapper.exists(memberId, req.campId(), req.checkIn())) {
                throw new InvalidRequestException("이미 같은 날짜로 등록한 캠핑입니다.");
            }
        } else {
            if (req.lat() == null || req.lng() == null) {
                throw new InvalidRequestException("캠핑장이나 자리를 골라 주세요.");
            }
            if (req.lat() < -90 || req.lat() > 90 || req.lng() < -180 || req.lng() > 180) {
                throw new InvalidRequestException("위치 값이 올바르지 않습니다.");
            }
            lat = round6(req.lat());
            lng = round6(req.lng());
            if (campPlanMapper.existsSpot(memberId, lat, lng, req.checkIn())) {
                throw new InvalidRequestException("이미 같은 날짜로 등록한 자리입니다.");
            }
        }

        CampPlan plan = new CampPlan();
        plan.setMemberId(memberId);
        plan.setCampId(req.campId());
        plan.setLat(lat);   // 캠핑장 등록이면 둘 다 null
        plan.setLng(lng);
        plan.setCampName(req.campName().trim());
        plan.setPlace(blankToNull(req.place()));
        plan.setCheckIn(req.checkIn());
        plan.setCheckOut(req.checkOut());
        plan.setMemo(blankToNull(req.memo()));
        campPlanMapper.insert(plan);
        return CampPlanResponse.from(plan);
    }

    // 날짜 · 메모만 고친다. 캠핑장(자리)은 그대로
    public CampPlanResponse update(String loginId, Long planId, CampPlanUpdateRequest req) {
        Long memberId = findMember(loginId).getId();
        CampPlan plan = campPlanMapper.findById(memberId, planId);
        if (plan == null) {
            throw new NotFoundException("등록한 캠핑을 찾을 수 없습니다.");
        }
        // 체크인을 바꿀 때만 지난 날짜를 막는다 (이미 시작한 캠핑의 인원만 고치는 경우는 허용)
        if (!req.checkIn().equals(plan.getCheckIn()) && req.checkIn().isBefore(LocalDate.now(KST))) {
            throw new InvalidRequestException("지난 날짜는 고를 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(req.checkIn(), req.checkOut()) < 1) {
            throw new InvalidRequestException("체크아웃은 체크인 다음 날부터 고를 수 있습니다.");
        }
        // 체크인을 바꾸면 같은 캠핑장 · 같은 체크인의 다른 일정과 겹치는지 본다 (DB 의 UNIQUE 와 같은 조건)
        if (!req.checkIn().equals(plan.getCheckIn())) {
            boolean dup = plan.getCampId() != null
                    ? campPlanMapper.exists(memberId, plan.getCampId(), req.checkIn())
                    : campPlanMapper.existsSpot(memberId, plan.getLat(), plan.getLng(), req.checkIn());
            if (dup) {
                throw new InvalidRequestException("이미 같은 날짜로 등록한 캠핑입니다.");
            }
        }

        plan.setCheckIn(req.checkIn());
        plan.setCheckOut(req.checkOut());
        plan.setMemo(blankToNull(req.memo()));
        campPlanMapper.update(plan);
        return CampPlanResponse.from(plan);
    }

    public void delete(String loginId, Long planId) {
        Long memberId = findMember(loginId).getId();
        // 후기 · 사진 줄은 DB 가 같이 지우지만(CASCADE) 사진 파일은 서버 폴더에 남으므로, 지우기 전에 이름을 받아 둔다
        List<String> photoFiles = reviewMapper.findFileNamesByPlan(memberId, planId);
        // 남의 등록이거나 없는 id 면 0줄 → 없는 것처럼 404
        if (campPlanMapper.delete(memberId, planId) == 0) {
            throw new NotFoundException("등록한 캠핑을 찾을 수 없습니다.");
        }
        photoFiles.forEach(photoStore::delete);
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
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
