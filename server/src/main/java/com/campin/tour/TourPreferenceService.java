package com.campin.tour;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;
import com.campin.tour.TourPreferenceOptions.Option;
import com.campin.tour.TourPreferenceOptions.Question;

import lombok.RequiredArgsConstructor;

// 관광지 탭의 여행 취향. 안전점수에는 쓰지 않고 관광지 순서에만 쓴다
@Service              // 스프링: 업무 규칙을 담는 객체로 등록한다
@RequiredArgsConstructor  // lombok: final 필드를 받는 생성자를 만든다
public class TourPreferenceService {

    private final MemberMapper memberMapper;
    private final TourPreferenceMapper preferenceMapper;

    public List<Question> questions() {
        return TourPreferenceOptions.QUESTIONS;
    }

    public TourPreferenceResponse get(String loginId) {
        List<TourPreference> rows = preferenceMapper.findByMemberId(findMember(loginId).getId());
        return new TourPreferenceResponse(
                pickedCodes(TourPreferenceOptions.ACTIVITY_QUESTION, rows),
                pickedCodes(TourPreferenceOptions.NATURE_QUESTION, rows));
    }

    // @Transactional: 지우기·다시 넣기를 하나로 묶는다 → 중간에 실패하면 원래 취향이 그대로 남는다
    @Transactional
    public TourPreferenceResponse save(String loginId, TourPreferenceRequest req) {
        Member member = findMember(loginId);
        List<String> activity = validated(TourPreferenceOptions.ACTIVITY_QUESTION, req.activity());
        List<String> nature = validated(TourPreferenceOptions.NATURE_QUESTION, req.nature());

        preferenceMapper.deleteByMemberId(member.getId());
        activity.forEach(code -> preferenceMapper.insert(member.getId(), TourPreferenceOptions.ACTIVITY, code));
        nature.forEach(code -> preferenceMapper.insert(member.getId(), TourPreferenceOptions.NATURE, code));
        return new TourPreferenceResponse(activity, nature);
    }

    // 화면에서도 막지만 주소로 직접 보내는 요청에 대비해 서버에서 한 번 더 검사한다
    private static List<String> validated(Question question, List<String> codes) {
        for (String code : codes) {
            if (code == null || !question.hasOption(code)) {
                throw new InvalidRequestException("없는 여행 취향입니다.");
            }
        }
        List<String> picked = inOptionOrder(question, codes::contains);
        if (picked.size() > question.max()) {
            throw new InvalidRequestException(question.title() + " 최대 " + question.max() + "개까지 고를 수 있습니다.");
        }
        return picked;
    }

    private static List<String> pickedCodes(Question question, List<TourPreference> rows) {
        return inOptionOrder(question, code -> rows.stream()
                .anyMatch(r -> r.getQuestion().equals(question.code()) && r.getOptionCode().equals(code)));
    }

    // 순서에는 의미가 없어서 항상 선택지 순서로 돌려준다. 같은 값을 두 번 보내도 하나로 친다
    private static List<String> inOptionOrder(Question question, Predicate<String> picked) {
        return question.options().stream().map(Option::code).filter(picked).toList();
    }

    private Member findMember(String loginId) {
        Member member = memberMapper.findByLoginId(loginId);
        if (member == null) {
            throw new NotFoundException("회원 정보를 찾을 수 없습니다.");
        }
        return member;
    }
}
