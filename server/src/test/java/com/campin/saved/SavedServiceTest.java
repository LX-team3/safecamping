package com.campin.saved;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;

// DB 없이 저장 목록 규칙만 확인한다 (mapper 는 메모리 List 로 대신함)
class SavedServiceTest {

    private final List<SavedList> lists = new ArrayList<>();
    private final List<String[]> camps = new ArrayList<>(); // [목록 id, 캠핑장 id]
    private final Map<String, CompareCamp> campTable = new HashMap<>(); // camp 테이블 대신 (id → 비교 값)
    private SavedService service;

    @BeforeEach
    void setUp() {
        Map<String, Member> members = new HashMap<>();
        members.put("me", member(1L, "me"));
        members.put("other", member(2L, "other"));

        MemberMapper fakeMemberMapper = new MemberMapper() {
            @Override public Member findByLoginId(String loginId) { return members.get(loginId); }
            @Override public boolean existsByLoginId(String loginId) { return members.containsKey(loginId); }
            @Override public void insert(Member member) { }
        };

        SavedMapper fakeSavedMapper = new SavedMapper() {
            @Override public List<SavedList> findLists(Long memberId) {
                return lists.stream().filter(l -> l.getMemberId().equals(memberId)).peek(l ->
                        l.setCampCount((int) camps.stream().filter(c -> c[0].equals(l.getId().toString())).count())).toList();
            }
            @Override public SavedList findList(Long memberId, Long listId) {
                return lists.stream().filter(l -> l.getMemberId().equals(memberId) && l.getId().equals(listId))
                        .findFirst().orElse(null);
            }
            @Override public boolean existsDefaultList(Long memberId) {
                return lists.stream().anyMatch(l -> l.getMemberId().equals(memberId) && l.getIsDefault());
            }
            @Override public Long findDefaultListId(Long memberId) {
                return lists.stream().filter(l -> l.getMemberId().equals(memberId) && l.getIsDefault())
                        .map(SavedList::getId).findFirst().orElse(null);
            }
            @Override public boolean existsCamp(String campId) {
                return campTable.containsKey(campId);
            }
            @Override public boolean existsListName(Long memberId, String name) {
                return lists.stream().anyMatch(l -> l.getMemberId().equals(memberId) && l.getName().equals(name));
            }
            @Override public int insertList(SavedList list) {
                if (existsListName(list.getMemberId(), list.getName())) return 0;
                list.setId((long) lists.size() + 1);
                lists.add(list);
                return 1;
            }
            @Override public int updateListName(Long memberId, Long listId, String name) {
                findList(memberId, listId).setName(name);
                return 1;
            }
            @Override public int deleteList(Long memberId, Long listId) {
                return lists.removeIf(l -> l.getId().equals(listId) && !l.getIsDefault()) ? 1 : 0;
            }
            @Override public List<String> findCampIds(Long listId) {
                return camps.stream().filter(c -> c[0].equals(listId.toString())).map(c -> c[1]).toList();
            }
            @Override public List<CompareCamp> findCompareCamps(Long listId) {
                return findCampIds(listId).stream().filter(campTable::containsKey).map(campTable::get).toList();
            }
            @Override public int insertCamp(Long listId, String campId) {
                if (findCampIds(listId).contains(campId)) return 0;
                camps.add(new String[] {listId.toString(), campId});
                return 1;
            }
            @Override public int deleteCamp(Long listId, String campId) {
                return camps.removeIf(c -> c[0].equals(listId.toString()) && c[1].equals(campId)) ? 1 : 0;
            }
        };
        service = new SavedService(fakeSavedMapper, fakeMemberMapper);

        // 비교 값이 서로 다른 캠핑장 3곳 (실제 데이터처럼 비어 있는 값도 섞는다)
        campTable.put("100568", camp("100568", "내린천 글램핑", 87, 8, 12, true, true));
        campTable.put("101137", camp("101137", "갯골자연휴양림 야영장", 85, 9, 5, true, false));
        campTable.put("8175", camp("8175", "진동리 국민여가캠핑장", 27, 46, null, null, false));
    }

    private static Member member(Long id, String loginId) {
        Member m = new Member();
        m.setId(id);
        m.setLoginId(loginId);
        return m;
    }

    private static CompareCamp camp(String id, String name, Integer score, Integer er, Integer fire,
                                    Boolean power, Boolean pet) {
        CompareCamp c = new CompareCamp();
        c.setId(id);
        c.setName(name);
        c.setSafetyScore(score);
        c.setErMinutes(er);
        c.setFireMinutes(fire);
        c.setHasPower(power);
        c.setAllowsPet(pet);
        return c;
    }

    private Long defaultListId(String loginId) {
        return service.findLists(loginId).get(0).id();
    }

    @Test
    void 처음_들어오면_관심_캠핑장_기본_목록이_하나_생긴다() {
        service.findLists("me");
        List<SavedListResponse> result = service.findLists("me");

        assertEquals(1, result.size());
        assertEquals("관심 캠핑장", result.get(0).name());
        assertTrue(result.get(0).isDefault());
    }

    @Test
    void 같은_이름의_목록은_만들_수_없다() {
        service.createList("me", "11월 가족 캠핑 후보");
        assertThrows(InvalidRequestException.class, () -> service.createList("me", " 11월 가족 캠핑 후보 "));
        assertThrows(InvalidRequestException.class, () -> service.createList("me", "관심 캠핑장"));
    }

    @Test
    void 기본_목록은_지우거나_이름을_바꿀_수_없다() {
        Long id = defaultListId("me");
        assertThrows(InvalidRequestException.class, () -> service.deleteList("me", id));
        assertThrows(InvalidRequestException.class, () -> service.renameList("me", id, "새 이름"));
    }

    @Test
    void 남의_목록은_없는_목록처럼_404() {
        Long othersList = defaultListId("other");
        assertThrows(NotFoundException.class, () -> service.addCamp("me", othersList, "100568"));
        assertThrows(NotFoundException.class, () -> service.compare("me", othersList));
    }

    @Test
    void 없는_캠핑장은_담을_수_없고_같은_캠핑장은_한_번만_담긴다() {
        Long id = defaultListId("me");
        assertThrows(NotFoundException.class, () -> service.addCamp("me", id, "c1")); // 예전 더미 id 도 이제 없는 캠핑장

        service.addCamp("me", id, "100568");
        service.addCamp("me", id, "100568");
        assertEquals(1, service.findLists("me").get(0).campCount());
    }

    @Test
    void 하트_상태는_기본_목록과_담긴_캠핑장을_돌려준다() {
        FavoritesResponse empty = service.favorites("me"); // 처음이면 기본 목록을 만든다
        assertEquals(List.of(), empty.campIds());

        service.addCamp("me", empty.listId(), "100568");
        service.addCamp("me", empty.listId(), "8175");
        FavoritesResponse result = service.favorites("me");
        assertEquals(empty.listId(), result.listId());
        assertEquals(List.of("100568", "8175"), result.campIds());
    }

    @Test
    void 비교표는_실제_값으로_가장_좋은_곳을_표시하고_빈_값은_확인_필요() {
        Long id = defaultListId("me");
        service.addCamp("me", id, "100568");
        service.addCamp("me", id, "101137");
        service.addCamp("me", id, "8175");

        CompareResponse result = service.compare("me", id);
        assertEquals(3, result.totalCount());
        assertEquals(6, result.rows().size());

        // 안전점수: 내린천 글램핑(87)이 가장 높다
        List<CompareResponse.Cell> score = result.rows().get(0).cells();
        assertEquals(List.of(true, false, false), score.stream().map(CompareResponse.Cell::best).toList());
        assertEquals("87점", score.get(0).text());
        // 응급실: 내린천 글램핑(8분)이 가장 가깝다. 막대는 분 숫자로
        List<CompareResponse.Cell> er = result.rows().get(1).cells();
        assertEquals(List.of(true, false, false), er.stream().map(CompareResponse.Cell::best).toList());
        assertEquals(List.of(8, 9, 46), er.stream().map(CompareResponse.Cell::value).toList());
        // 소방서: 값이 없는 곳은 "확인 필요"이고 가장 좋은 값 후보에서 빠진다
        List<CompareResponse.Cell> fire = result.rows().get(2).cells();
        assertEquals(List.of(false, true, false), fire.stream().map(CompareResponse.Cell::best).toList());
        assertEquals("확인 필요", fire.get(2).text());
        // 대피소: 데이터가 없어 모두 "확인 필요", 표시 없음
        List<CompareResponse.Cell> shelter = result.rows().get(3).cells();
        assertTrue(shelter.stream().allMatch(c -> c.text().equals("확인 필요") && !c.best() && c.value() == null));
        // 전기: 원본에 값이 없으면 "확인 필요"
        assertEquals(List.of("가능", "가능", "확인 필요"),
                result.rows().get(4).cells().stream().map(CompareResponse.Cell::text).toList());
    }

    @Test
    void 비교는_camp_테이블에_있는_캠핑장만_앞의_3곳까지() {
        Long id = defaultListId("me");
        camps.add(new String[] {id.toString(), "c1"}); // 예전에 담아 둔 더미 id (camp 테이블에 없음)
        service.addCamp("me", id, "100568");
        service.addCamp("me", id, "101137");
        campTable.put("3383", camp("3383", "하추자연휴양림 야영장", 52, 25, 9, true, false));
        campTable.put("100886", camp("100886", "햇살글램핑", 76, 12, 7, false, false));
        service.addCamp("me", id, "3383");
        service.addCamp("me", id, "100886");

        CompareResponse result = service.compare("me", id);
        assertEquals(4, result.totalCount()); // c1 은 세지 않는다
        assertEquals(List.of("100568", "101137", "3383"),
                result.camps().stream().map(CompareResponse.Camp::id).toList());
    }

    @Test
    void 캠핑장이_한_곳이면_가장_좋은_값을_표시하지_않는다() {
        Long id = defaultListId("me");
        service.addCamp("me", id, "100568");

        CompareResponse result = service.compare("me", id);
        assertFalse(result.rows().stream().flatMap(r -> r.cells().stream()).anyMatch(CompareResponse.Cell::best));
    }
}
