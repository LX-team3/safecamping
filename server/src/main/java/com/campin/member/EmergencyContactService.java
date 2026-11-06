package com.campin.member;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;

import lombok.RequiredArgsConstructor;

// 내 정보 · 비상연락처 (최대 5명). emergency_contact 테이블을 읽고 통째로 바꾼다
@Service
@RequiredArgsConstructor
public class EmergencyContactService {

    static final int MAX = 5;
    private static final int NAME_MAX = 20;

    private final MemberMapper memberMapper;
    private final EmergencyContactMapper contactMapper;

    public List<EmergencyContact> get(String loginId) {
        return contactMapper.findByMemberId(findMember(loginId).getId());
    }

    // @Transactional: 지우기·다시 넣기를 하나로 묶는다 → 중간에 실패하면 원래 목록이 그대로 남는다
    @Transactional
    public List<EmergencyContact> update(String loginId, EmergencyContactRequest req) {
        Member m = findMember(loginId);

        // 모두 검사한 뒤에 저장한다. 이름·번호를 다 비운 칸(추가만 하고 안 쓴 칸)은 건너뛴다
        List<EmergencyContact> filled = new ArrayList<>();
        for (EmergencyContact c : req.contacts()) {
            String name = c == null || c.getName() == null ? "" : c.getName().trim();
            String phone = c == null || c.getPhone() == null ? "" : SignupService.toDigits(c.getPhone());
            if (name.isEmpty() && phone.isEmpty()) {
                continue;
            }
            if (name.isEmpty()) {
                throw new InvalidRequestException("비상연락처 이름을 입력해 주세요.");
            }
            if (name.length() > NAME_MAX) {
                throw new InvalidRequestException("이름은 20자까지 입력할 수 있습니다.");
            }
            if (!phone.matches(SignupService.PHONE_PATTERN)) {
                throw new InvalidRequestException("비상연락처 번호를 다시 확인해 주세요.");
            }
            filled.add(new EmergencyContact(name, phone));
        }
        if (filled.size() > MAX) {  // @Size 로도 막지만 서비스만 따로 불릴 때를 위해 한 번 더
            throw new InvalidRequestException("비상연락처는 5명까지 넣을 수 있습니다.");
        }

        contactMapper.deleteByMemberId(m.getId());
        for (EmergencyContact c : filled) {
            contactMapper.insert(m.getId(), c);
        }
        return filled;
    }

    private Member findMember(String loginId) {
        Member m = memberMapper.findByLoginId(loginId);
        if (m == null) {  // 로그인한 사이 탈퇴 등으로 회원이 없어진 경우
            throw new NotFoundException("회원 정보를 찾을 수 없습니다.");
        }
        return m;
    }
}
