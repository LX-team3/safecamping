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

// 저장 화면 "목록" 탭과 관심 캠핑장 비교
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

    public void addCamp(String loginId, Long listId, String campId) {
        SavedList list = findOwnList(memberId(loginId), listId);
        if (CampDummyData.find(campId) == null) {
            throw new NotFoundException("캠핑장을 찾을 수 없습니다.");
        }
        savedMapper.insertCamp(list.getId(), campId);
    }

    public void removeCamp(String loginId, Long listId, String campId) {
        SavedList list = findOwnList(memberId(loginId), listId);
        savedMapper.deleteCamp(list.getId(), campId);
    }

    // 목록에 담긴 캠핑장 중 앞의 3곳을 표로 비교한다
    public CompareResponse compare(String loginId, Long listId) {
        SavedList list = findOwnList(memberId(loginId), listId);
        List<String> campIds = savedMapper.findCampIds(list.getId());
        List<CampDummyData.Camp> camps = campIds.stream()
                .map(CampDummyData::find)
                .filter(Objects::nonNull)
                .limit(COMPARE_MAX)
                .toList();

        List<CompareResponse.Row> rows = camps.isEmpty() ? List.of() : List.of(
                // 안전점수 줄은 설계대로 모두 초록 글씨로만 보여 주고 "가장 좋은 값" 표시는 하지 않는다
                row("안전점수", "score", camps, c -> "안전 " + c.safetyScore(), c -> false),
                minutesRow("응급실", camps, CampDummyData.Camp::erMinutes),
                minutesRow("소방서", camps, CampDummyData.Camp::fireMinutes),
                shelterRow(camps),
                yesNoRow("전기 사용", camps, CampDummyData.Camp::hasPower),
                yesNoRow("반려동물", camps, CampDummyData.Camp::allowsPet));

        return new CompareResponse(true, list.getId(), list.getName(), campIds.size(),
                camps.stream().map(c -> new CompareResponse.Camp(c.id(), c.name())).toList(), rows);
    }

    // 차로 가는 시간: 가장 짧은 곳이 가장 좋은 값 (같으면 모두)
    private CompareResponse.Row minutesRow(String label, List<CampDummyData.Camp> camps,
                                           Function<CampDummyData.Camp, Integer> minutes) {
        int min = camps.stream().mapToInt(minutes::apply).min().orElse(0);
        return row(label, "normal", camps, c -> "차로 " + minutes.apply(c) + "분", c -> minutes.apply(c) == min);
    }

    // 대피소: 걸어서 갈 수 있는 곳을 먼저 친다 (재난 때는 차가 막히거나 못 쓸 수 있어서 — 임시 규칙, 설계 파일 표시와 같음)
    // 걸어서 가는 곳이 있으면 그중 가장 짧은 곳, 없으면 차로 가장 짧은 곳
    private CompareResponse.Row shelterRow(List<CampDummyData.Camp> camps) {
        boolean anyOnFoot = camps.stream().anyMatch(CampDummyData.Camp::shelterOnFoot);
        int min = camps.stream()
                .filter(c -> c.shelterOnFoot() == anyOnFoot)
                .mapToInt(CampDummyData.Camp::shelterMinutes)
                .min().orElse(0);
        return row("대피소", "normal", camps,
                c -> (c.shelterOnFoot() ? "걸어서 " : "차로 ") + c.shelterMinutes() + "분",
                c -> c.shelterOnFoot() == anyOnFoot && c.shelterMinutes() == min);
    }

    private CompareResponse.Row yesNoRow(String label, List<CampDummyData.Camp> camps,
                                         Predicate<CampDummyData.Camp> yes) {
        return row(label, "normal", camps, c -> yes.test(c) ? "가능" : "불가", yes);
    }

    // 캠핑장이 1곳뿐이면 비교할 대상이 없으므로 "가장 좋은 값" 표시를 하지 않는다
    private CompareResponse.Row row(String label, String kind, List<CampDummyData.Camp> camps,
                                    Function<CampDummyData.Camp, String> text,
                                    Predicate<CampDummyData.Camp> best) {
        List<CompareResponse.Cell> cells = new ArrayList<>();
        for (CampDummyData.Camp c : camps) {
            cells.add(new CompareResponse.Cell(text.apply(c), camps.size() > 1 && best.test(c)));
        }
        return new CompareResponse.Row(label, kind, cells);
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
