package com.campin.member;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 테스트용 emergency_contact 테이블 (회원 id → 넣은 순서대로의 목록)
class FakeEmergencyContactMapper implements EmergencyContactMapper {

    final Map<Long, List<EmergencyContact>> table = new HashMap<>();

    @Override
    public List<EmergencyContact> findByMemberId(Long memberId) {
        return new ArrayList<>(table.getOrDefault(memberId, List.of()));
    }

    @Override
    public void deleteByMemberId(Long memberId) {
        table.remove(memberId);
    }

    @Override
    public void insert(Long memberId, EmergencyContact contact) {
        table.computeIfAbsent(memberId, k -> new ArrayList<>()).add(contact);
    }
}
