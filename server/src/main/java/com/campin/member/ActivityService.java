package com.campin.member;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;

import lombok.RequiredArgsConstructor;

// 내 정보 · 여행 취향 (6개 중 최대 3개, 순서 없음). 고른 취향에 맞는 캠핑장 추천은 캠핑장 데이터가 들어오면 붙인다
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final MemberMapper memberMapper;
    private final ActivityMapper activityMapper;

    public ActivityResponse get(String loginId) {
        return ActivityResponse.of(selectedInOptionOrder(activityMapper.findByMemberId(findMember(loginId).getId())));
    }

    // @Transactional: 지우기·다시 넣기를 하나로 묶는다 → 중간에 실패하면 원래 취향이 그대로 남는다
    @Transactional
    public ActivityResponse update(String loginId, ActivityRequest req) {
        Member m = findMember(loginId);

        // 같은 값을 두 번 보내도 하나로 친다 (LinkedHashSet)
        Set<String> activities = new LinkedHashSet<>();
        for (String t : req.activities()) {
            if (t == null || !ActivityOption.isValid(t)) {
                throw new InvalidRequestException("없는 여행 취향입니다.");
            }
            activities.add(t);
        }
        if (activities.size() > ActivityOption.MAX) {
            throw new InvalidRequestException("여행 취향은 3개까지 고를 수 있습니다.");
        }

        activityMapper.deleteByMemberId(m.getId());
        for (String t : activities) {
            activityMapper.insert(m.getId(), t);
        }
        return ActivityResponse.of(selectedInOptionOrder(new ArrayList<>(activities)));
    }

    // 순서는 의미가 없으므로 항상 선택지 순서로 돌려준다 (화면·테스트에서 순서가 흔들리지 않게)
    private static List<String> selectedInOptionOrder(List<String> selected) {
        List<String> result = new ArrayList<>();
        for (ActivityOption o : ActivityOption.values()) {
            if (selected.contains(o.code())) {
                result.add(o.code());
            }
        }
        return result;
    }

    private Member findMember(String loginId) {
        Member m = memberMapper.findByLoginId(loginId);
        if (m == null) {
            throw new NotFoundException("회원 정보를 찾을 수 없습니다.");
        }
        return m;
    }
}
