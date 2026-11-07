package com.campin.member;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campin.common.InvalidRequestException;

import lombok.RequiredArgsConstructor;

// 내 정보 › 함께 가는 가족: 아이 나이대 저장·불러오기 (누구와 언제 시트에서 "지난번 정보"로 씀)
@Service
@RequiredArgsConstructor
public class MemberChildService {

    // 영유아 0~3세 / 미취학 4~7세 / 초등 8~12세 (db/schema.sql 의 CHECK 와 같게)
    private static final Set<String> AGE_GROUPS = Set.of("infant", "preschool", "elementary");

    private final MemberChildMapper memberChildMapper;

    public List<String> find(String loginId) {
        return memberChildMapper.findAgeGroups(loginId);
    }

    // @Transactional: 지우기와 새로 넣기가 함께 성공하거나 함께 취소되게 묶는다
    @Transactional
    public List<String> save(String loginId, List<String> ageGroups) {
        if (!AGE_GROUPS.containsAll(ageGroups)) {
            throw new InvalidRequestException("없는 나이대입니다.");
        }
        memberChildMapper.deleteByLoginId(loginId);
        for (int i = 0; i < ageGroups.size(); i++) {
            memberChildMapper.insert(loginId, i + 1, ageGroups.get(i));
        }
        return ageGroups;
    }
}
