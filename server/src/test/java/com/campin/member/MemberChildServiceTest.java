package com.campin.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;

// 아이 나이대 저장 규칙 (mapper 는 메모리 목록으로 대신함)
class MemberChildServiceTest {

    private final List<String> table = new ArrayList<>(List.of("infant", "infant", "infant"));

    private final MemberChildService service = new MemberChildService(new MemberChildMapper() {
        @Override public List<String> findAgeGroups(String loginId) { return table; }
        @Override public void deleteByLoginId(String loginId) { table.clear(); }
        @Override public void insert(String loginId, int sortOrder, String ageGroup) { table.add(ageGroup); }
    });

    @Test
    void 저장하면_예전_아이_정보를_통째로_바꾼다() {
        service.save("camper01", List.of("preschool", "elementary"));
        assertEquals(List.of("preschool", "elementary"), service.find("camper01"));
    }

    @Test
    void 없는_나이대는_저장하지_않는다() {
        assertThrows(InvalidRequestException.class, () -> service.save("camper01", List.of("teen")));
        assertEquals(3, table.size()); // 예전 정보 그대로
    }
}
