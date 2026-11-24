package com.campin.family;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

// 가족 캠핑 공유: 같은 가족방 사람이 "내 캠핑에 일정 등록"한 캠핑을 보기만 한다
// 방에 들어가 있으면 자동으로 서로 보인다 (초대코드로 들어온 사람끼리라서). 가족방이 없으면 빈 목록
@Service                   // 스프링: 비즈니스 로직 담당 빈으로 등록
@RequiredArgsConstructor   // Lombok: final 필드를 받는 생성자를 만든다 → 스프링이 그 생성자로 주입
public class FamilyCampPlanService {

    private final FamilyCampPlanMapper mapper;
    private final FamilyService familyService;

    public List<FamilyCampPlanResponse> findOthers(String loginId) {
        MyRoom my = familyService.findMyRoomOrNull(loginId);
        if (my == null) return List.of();
        return mapper.findOthers(my.roomId(), my.memberId()).stream().map(FamilyCampPlanResponse::from).toList();
    }
}
