package com.campin.family;

import java.util.List;

import org.springframework.stereotype.Service;

import com.campin.common.NotFoundException;

import lombok.RequiredArgsConstructor;

// 가족 일정 공유 (가족방 2차). "내가 들어가 있는 방"의 일정만 다룬다
// 지난 일정도 넣을 수 있게 둔다 (캠핑 중 "몇 시에 무엇을 했다" 기록용으로도 쓸 수 있어서)
@Service                   // 스프링: 비즈니스 로직 담당 빈으로 등록
@RequiredArgsConstructor   // Lombok: final 필드를 받는 생성자를 만든다 → 스프링이 그 생성자로 주입
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final FamilyService familyService;

    public List<ScheduleResponse> findAll(String loginId) {
        MyRoom my = familyService.requireMyRoom(loginId);
        return scheduleMapper.findAll(my.roomId()).stream().map(ScheduleResponse::from).toList();
    }

    public ScheduleResponse add(String loginId, ScheduleRequest req) {
        MyRoom my = familyService.requireMyRoom(loginId);
        FamilySchedule s = new FamilySchedule();
        s.setFamilyRoomId(my.roomId());
        s.setTitle(req.title().trim());
        s.setStartAt(req.startAt());
        s.setMemo(ChildCardService.blankToNull(req.memo()));
        s.setCreatedBy(my.memberId());
        scheduleMapper.insert(s);
        s.setCreatedByName(my.memberName());
        return ScheduleResponse.from(s);
    }

    public void delete(String loginId, Long scheduleId) {
        MyRoom my = familyService.requireMyRoom(loginId);
        // 다른 가족방 일정이거나 없는 일정이면 0줄 → 없는 것처럼 404
        if (scheduleMapper.delete(my.roomId(), scheduleId) == 0) {
            throw new NotFoundException("일정을 찾을 수 없습니다.");
        }
    }
}
