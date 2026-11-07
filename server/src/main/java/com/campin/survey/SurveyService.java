package com.campin.survey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campin.common.InvalidRequestException;

import lombok.RequiredArgsConstructor;

// ② 1분 진단 · 관광지 탭 여행 취향 설문
@Service
@RequiredArgsConstructor
public class SurveyService {

    private final SurveyMapper surveyMapper;

    // 설문 화면에 그릴 내용 (단계 → 질문 → 선택지)
    public SurveyResponse getSurvey(String code) {
        Survey survey = findSurvey(code);
        List<SurveyQuestion> questions = surveyMapper.findQuestions(survey.getId());
        Map<Long, List<SurveyOption>> optionsByQuestion = optionsByQuestion(survey);

        List<SurveyResponse.Step> steps = surveyMapper.findSteps(survey.getId()).stream()
                .map(st -> new SurveyResponse.Step(st.getStep(), st.getTitle(), st.getSub(), st.getNote(),
                        questions.stream()
                                .filter(q -> q.getStepId().equals(st.getId()))
                                .map(q -> new SurveyResponse.Question(q.getCode(), q.getTitle(), q.getInputType(),
                                        q.getMaxSelect(), q.isRequired(), q.getNote(),
                                        optionsByQuestion.getOrDefault(q.getId(), List.of()).stream()
                                                .map(SurveyResponse.Option::from).toList()))
                                .toList()))
                .toList();

        return new SurveyResponse(survey.getCode(), survey.getTitle(), survey.getDurationText(),
                survey.getFinishLabel(), survey.isNumbered(), steps);
    }

    // 답변 제출: 화면에서도 막지만 서버에서 한 번 더 규칙을 검사한다 (주소로 직접 보내는 요청 대비)
    // loginId 가 있으면(로그인 회원) DB 에 저장하고, 없으면 검사만 하고 saved=false
    // @Transactional: 답변 1건과 선택지 여러 줄이 전부 들어가거나 전부 안 들어가게 묶는다
    @Transactional
    public Map<String, Boolean> submit(String code, SurveyAnswerRequest request, String loginId) {
        Survey survey = findSurvey(code);
        List<Picked> picked = validate(surveyMapper.findQuestions(survey.getId()), optionsByQuestion(survey),
                request.answers());

        if (loginId == null) {
            return Map.of("saved", false);
        }
        SurveyAnswer answer = new SurveyAnswer(null, survey.getId(), loginId);
        if (surveyMapper.insertAnswer(answer) == 0) {
            return Map.of("saved", false); // 세션은 있는데 회원이 지워진 경우
        }
        for (Picked p : picked) {
            surveyMapper.insertAnswerItem(answer.getId(), p.optionId(), p.rank());
        }
        return Map.of("saved", true);
    }

    // 내가 지난번에 낸 답 → 제출 때와 같은 모양 { "질문 code": ["선택지 code", ...] }. 없으면 빈 객체
    public Map<String, List<String>> myAnswers(String code, String loginId) {
        Survey survey = findSurvey(code);
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (SurveyAnswerItem item : surveyMapper.findLatestAnswerItems(survey.getId(), loginId)) {
            result.computeIfAbsent(item.getQuestionCode(), k -> new ArrayList<>()).add(item.getOptionCode());
        }
        return result;
    }

    private Survey findSurvey(String code) {
        Survey survey = surveyMapper.findSurveyByCode(code);
        if (survey == null) {
            throw new InvalidRequestException("없는 설문입니다.");
        }
        return survey;
    }

    private Map<Long, List<SurveyOption>> optionsByQuestion(Survey survey) {
        return surveyMapper.findOptions(survey.getId()).stream()
                .collect(Collectors.groupingBy(SurveyOption::getQuestionId));
    }

    // 고른 선택지 하나: 저장할 option id 와 고른 순서
    record Picked(Long optionId, int rank) {
    }

    // 질문마다: 필수인데 안 골랐는지 / 최대 개수를 넘었는지 / 없는 선택지인지 / 단독 선택지를 다른 것과 같이 골랐는지
    private List<Picked> validate(List<SurveyQuestion> questions, Map<Long, List<SurveyOption>> optionsByQuestion,
                                  Map<String, List<String>> answers) {
        Map<String, SurveyQuestion> questionByCode = questions.stream()
                .collect(Collectors.toMap(SurveyQuestion::getCode, Function.identity()));
        for (String questionCode : answers.keySet()) {
            if (!questionByCode.containsKey(questionCode)) {
                throw new InvalidRequestException("없는 질문입니다: " + questionCode);
            }
        }

        List<Picked> picked = new ArrayList<>();
        for (SurveyQuestion q : questions) {
            List<String> codes = answers.get(q.getCode());
            if (codes == null) {
                codes = List.of();
            }
            if (q.isRequired() && codes.isEmpty()) {
                throw new InvalidRequestException("'" + q.getTitle() + "' 에 답해 주세요.");
            }
            if (q.getMaxSelect() > 0 && codes.size() > q.getMaxSelect()) {
                throw new InvalidRequestException("'" + q.getTitle() + "' 는 " + q.getMaxSelect() + "개까지 고를 수 있어요.");
            }
            if (new HashSet<>(codes).size() != codes.size()) {
                throw new InvalidRequestException("같은 선택지를 두 번 고를 수 없어요.");
            }

            Map<String, SurveyOption> optionByCode = optionsByQuestion.getOrDefault(q.getId(), List.of()).stream()
                    .collect(Collectors.toMap(SurveyOption::getCode, Function.identity()));
            for (int i = 0; i < codes.size(); i++) {
                SurveyOption option = optionByCode.get(codes.get(i));
                if (option == null) {
                    throw new InvalidRequestException("없는 선택지입니다: " + codes.get(i));
                }
                if (option.isExclusive() && codes.size() > 1) {
                    throw new InvalidRequestException("'" + option.getLabel() + "' 는 다른 것과 함께 고를 수 없어요.");
                }
                picked.add(new Picked(option.getId(), i + 1));
            }
        }
        return picked;
    }
}
