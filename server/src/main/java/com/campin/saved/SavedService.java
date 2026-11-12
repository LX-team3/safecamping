package com.campin.saved;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;

import lombok.RequiredArgsConstructor;

// 저장 화면 "목록" 탭 · 하트(관심 캠핑장) · 관심 캠핑장 비교
// 로그인 정보에는 아이디(loginId)만 있으므로 먼저 회원 번호(member.id)로 바꿔서 쓴다
@Service
@RequiredArgsConstructor
public class SavedService {

    static final String DEFAULT_LIST_NAME = "관심 캠핑장";
    static final int COMPARE_MAX = 3;  // 설계상 비교 표는 3곳까지

    private final SavedMapper savedMapper;
    private final MemberMapper memberMapper;

    public List<SavedListResponse> findLists(String loginId) {
        Long memberId = memberId(loginId);
        ensureDefaultList(memberId);
        return savedMapper.findLists(memberId).stream().map(SavedListResponse::from).toList();
    }

    public SavedListResponse createList(String loginId, String name) {
        Long memberId = memberId(loginId);
        ensureDefaultList(memberId); // 기본 목록 이름을 먼저 차지해 두어 같은 이름의 일반 목록이 생기지 않게 한다
        String trimmed = name.trim();
        if (savedMapper.existsListName(memberId, trimmed)) {
            throw new InvalidRequestException("같은 이름의 목록이 있습니다.");
        }
        SavedList list = new SavedList();
        list.setMemberId(memberId);
        list.setName(trimmed);
        list.setIsDefault(false);
        // 위 확인과 INSERT 사이에 같은 이름이 먼저 들어가면 0줄 → 같은 문구로 알린다
        if (savedMapper.insertList(list) == 0) {
            throw new InvalidRequestException("같은 이름의 목록이 있습니다.");
        }
        list.setCampCount(0);
        return SavedListResponse.from(list);
    }

    public void renameList(String loginId, Long listId, String name) {
        Long memberId = memberId(loginId);
        SavedList list = findOwnList(memberId, listId);
        if (Boolean.TRUE.equals(list.getIsDefault())) {
            throw new InvalidRequestException("기본 목록은 이름을 바꿀 수 없습니다.");
        }
        String trimmed = name.trim();
        if (!trimmed.equals(list.getName()) && savedMapper.existsListName(memberId, trimmed)) {
            throw new InvalidRequestException("같은 이름의 목록이 있습니다.");
        }
        savedMapper.updateListName(memberId, listId, trimmed);
    }

    public void deleteList(String loginId, Long listId) {
        Long memberId = memberId(loginId);
        SavedList list = findOwnList(memberId, listId);
        if (Boolean.TRUE.equals(list.getIsDefault())) {
            throw new InvalidRequestException("기본 목록은 삭제할 수 없습니다.");
        }
        savedMapper.deleteList(memberId, listId);
    }

    public void addCamp(String loginId, Long listId, Long campId) {
        SavedList list = findOwnList(memberId(loginId), listId);
        if (!savedMapper.existsCamp(campId)) {
            throw new NotFoundException("캠핑장을 찾을 수 없습니다.");
        }
        savedMapper.insertCamp(list.getId(), campId);
    }

    public void removeCamp(String loginId, Long listId, Long campId) {
        SavedList list = findOwnList(memberId(loginId), listId);
        savedMapper.deleteCamp(list.getId(), campId);
    }

    // 하트 상태: '관심 캠핑장' 기본 목록(없으면 만든다)과 담긴 캠핑장 id
    public FavoritesResponse favorites(String loginId) {
        Long memberId = memberId(loginId);
        ensureDefaultList(memberId);
        Long listId = savedMapper.findDefaultListId(memberId);
        return new FavoritesResponse(listId, savedMapper.findCampIds(listId));
    }

    // 목록에 담긴 캠핑장 중 앞의 3곳을 비교한다. 값은 DB(camp · camp_facility · raw.camps)의 실제 데이터
    // 대피소는 아직 데이터가 없어 "확인 필요"로만 보여 준다
    public CompareResponse compare(String loginId, Long listId) {
        SavedList list = findOwnList(memberId(loginId), listId);
        List<CompareCamp> all = savedMapper.findCompareCamps(list.getId());
        List<CompareCamp> camps = all.stream().limit(COMPARE_MAX).toList();

        List<CompareResponse.Row> rows = camps.isEmpty() ? List.of() : List.of(
                scoreRow(camps),
                minutesRow("응급실까지", camps, CompareCamp::getErMinutes),
                minutesRow("소방서까지", camps, CompareCamp::getFireMinutes),
                row("대피소까지", "minutes", "가장 가까움", camps, c -> UNKNOWN, c -> null, c -> false),
                yesNoRow("전기 사용", camps, CompareCamp::getHasPower),
                yesNoRow("반려동물", camps, CompareCamp::getAllowsPet));

        return new CompareResponse(list.getId(), list.getName(), all.size(),
                camps.stream().map(c -> new CompareResponse.Camp(c.getId(), c.getName())).toList(), rows);
    }

    static final String UNKNOWN = "확인 필요";

    // 안전점수: 가장 높은 곳에 칩. 점수가 없는 곳은 "확인 필요"
    private CompareResponse.Row scoreRow(List<CompareCamp> camps) {
        Integer max = camps.stream().map(CompareCamp::getSafetyScore).filter(Objects::nonNull)
                .max(Integer::compare).orElse(null);
        return row("안전점수", "score", "가장 높음", camps,
                c -> c.getSafetyScore() == null ? UNKNOWN : c.getSafetyScore() + "점",
                CompareCamp::getSafetyScore,
                c -> c.getSafetyScore() != null && c.getSafetyScore().equals(max));
    }

    // 차로 가는 시간(추정): 가장 짧은 곳이 가장 좋은 값 (같으면 모두). 값이 없는 곳은 "확인 필요"
    private CompareResponse.Row minutesRow(String label, List<CompareCamp> camps,
                                           Function<CompareCamp, Integer> minutes) {
        Integer min = camps.stream().map(minutes).filter(Objects::nonNull).min(Integer::compare).orElse(null);
        return row(label, "minutes", "가장 가까움", camps,
                c -> minutes.apply(c) == null ? UNKNOWN : "차로 약 " + minutes.apply(c) + "분",
                minutes, c -> minutes.apply(c) != null && minutes.apply(c).equals(min));
    }

    // 가능/불가. 원본에 값이 없으면 "확인 필요"
    private CompareResponse.Row yesNoRow(String label, List<CompareCamp> camps,
                                         Function<CompareCamp, Boolean> yes) {
        return row(label, "yesno", "가능", camps,
                c -> yes.apply(c) == null ? UNKNOWN : (yes.apply(c) ? "가능" : "불가"),
                c -> null, c -> Boolean.TRUE.equals(yes.apply(c)));
    }

    // 캠핑장이 1곳뿐이면 비교할 대상이 없으므로 "가장 좋은 값" 표시를 하지 않는다
    private CompareResponse.Row row(String label, String kind, String bestLabel, List<CompareCamp> camps,
                                    Function<CompareCamp, String> text,
                                    Function<CompareCamp, Integer> value,
                                    Predicate<CompareCamp> best) {
        List<CompareResponse.Cell> cells = new ArrayList<>();
        for (CompareCamp c : camps) {
            cells.add(new CompareResponse.Cell(text.apply(c), value.apply(c), camps.size() > 1 && best.test(c)));
        }
        return new CompareResponse.Row(label, kind, bestLabel, cells);
    }

    // 처음 저장 화면에 들어온 회원에게 '관심 캠핑장' 목록을 만들어 준다
    private void ensureDefaultList(Long memberId) {
        if (savedMapper.existsDefaultList(memberId)) {
            return;
        }
        SavedList list = new SavedList();
        list.setMemberId(memberId);
        list.setName(DEFAULT_LIST_NAME);
        list.setIsDefault(true);
        savedMapper.insertList(list); // 동시에 두 번 들어와도 ON CONFLICT 로 하나만 생긴다
    }

    // 남의 목록이거나 없는 목록이면 똑같이 404 (남의 목록 id 가 있는지 알려 주지 않는다)
    private SavedList findOwnList(Long memberId, Long listId) {
        SavedList list = savedMapper.findList(memberId, listId);
        if (list == null) {
            throw new NotFoundException("목록을 찾을 수 없습니다.");
        }
        return list;
    }

    private Long memberId(String loginId) {
        Member member = memberMapper.findByLoginId(loginId);
        if (member == null) {
            throw new NotFoundException("회원 정보를 찾을 수 없습니다.");
        }
        return member.getId();
    }
}
