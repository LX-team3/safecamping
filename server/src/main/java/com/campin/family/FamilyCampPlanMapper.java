package com.campin.family;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// 가족 캠핑: 같은 가족방 구성원의 camp_plan 을 읽기만 한다 (고치기·지우기는 본인만 — mycamping 의 CampPlanMapper)
// 방 번호는 FamilyService 가 로그인한 회원의 방으로 찾아 넘긴다 → 다른 방 일정에는 닿지 않는다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface FamilyCampPlanMapper {

    // 내 것은 빼고(내 캠핑 목록에 이미 있음) 체크인 빠른 순
    @Select("""
            SELECT cp.id, cp.member_id AS ownerId, m.name AS ownerName,
                   cp.camp_id AS campId, cp.camp_name AS campName, cp.place, cp.lat, cp.lng,
                   cp.check_in AS checkIn, cp.check_out AS checkOut, cp.memo
              FROM camp_plan cp
              JOIN family_member fm ON fm.member_id = cp.member_id
              JOIN member m ON m.id = cp.member_id
             WHERE fm.family_room_id = #{roomId}
               AND cp.member_id <> #{memberId}
             ORDER BY cp.check_in, cp.id
            """)
    List<FamilyCampPlan> findOthers(@Param("roomId") Long roomId, @Param("memberId") Long memberId);
}
