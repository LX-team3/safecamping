package com.campin.survey;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;

import lombok.RequiredArgsConstructor;

// 1분 진단. 답 검사·저장만 한다 (유형 판정 기준은 아직 없어 화면은 답 요약만 보여 줌)
// 화면에서 하는 검사(개수·혼자만 고르는 값)를 서버에서도 같은 규칙으로 한 번 더 한다
@Service
@RequiredArgsConstructor
public class SurveyService {

    private final SurveyMapper surveyMapper;
    private final MemberMapper memberMapper;

    public List<SurveyQuestion> questions() {
        return SurveyQuestions.ALL;
    }

    // 진단한 적이 없으면 null
    public SurveyAnswers get(String loginId) {
        SurveyAnswerRow row = surveyMapper.findByMemberId(findMember(loginId).getId());
        if (row == null) {
            return null;
        }
        return new SurveyAnswers(row.getCampType(), row.getTransport(),
                split(row.getHeating()), split(row.getSafetyItems()), split(row.getFacilities()));
    }

    @Transactional
    public SurveyAnswers save(String loginId, SurveyAnswers answers) {
        Member m = findMember(loginId);
        check("campType", answers.campType() == null ? List.of() : List.of(answers.campType()));
        check("transport", answers.transport() == null ? List.of() : List.of(answers.transport()));
        check("heating", answers.heating());
        check("safetyItems", answers.safetyItems());
        check("facilities", answers.facilities());

        SurveyAnswerRow row = new SurveyAnswerRow();
        row.setMemberId(m.getId());
        row.setCampType(answers.campType());
        row.setTransport(answers.transport());
        row.setHeating(String.join(",", answers.heating()));
        row.setSafetyItems(String.join(",", answers.safetyItems()));
        row.setFacilities(String.join(",", answers.facilities()));
        surveyMapper.upsert(row);
        return answers;
    }

    // 질문 하나의 답 검사: 1개 이상 · 최대 개수 · 없는 값 · 같은 값 두 번 · 혼자만 고르는 값(예: 아직 없어요)
    private static void check(String key, List<String> picked) {
        SurveyQuestion q = SurveyQuestions.get(key);
        if (picked == null || picked.isEmpty()) {
            throw new InvalidRequestException("'" + q.title() + "'에 답해 주세요.");
        }
        if (picked.size() > q.max()) {
            throw new InvalidRequestException("'" + q.title() + "'은(는) " + q.max() + "개까지 고를 수 있습니다.");
        }
        if (new HashSet<>(picked).size() != picked.size() || !picked.stream().allMatch(q::hasCode)) {
            throw new InvalidRequestException("'" + q.title() + "'의 답을 다시 확인해 주세요.");
        }
        if (q.exclusive() != null && picked.contains(q.exclusive()) && picked.size() > 1) {
            throw new InvalidRequestException("'" + q.title() + "'의 답을 다시 확인해 주세요.");
        }
    }

    private static List<String> split(String joined) {
        return joined == null || joined.isEmpty() ? List.of() : Arrays.asList(joined.split(","));
    }

    private Member findMember(String loginId) {
        Member m = memberMapper.findByLoginId(loginId);
        if (m == null) {
            throw new NotFoundException("회원 정보를 찾을 수 없습니다.");
        }
        return m;
    }
}
