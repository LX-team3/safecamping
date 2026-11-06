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
    }

    private static Member member(Long id, String loginId) {
        Member m = new Member();
        m.setId(id);
        m.setLoginId(loginId);
        return m;
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
        assertThrows(NotFoundException.class, () -> service.addCamp("me", othersList, "c1"));
        assertThrows(NotFoundException.class, () -> service.compare("me", othersList));
    }

    @Test
    void 없는_캠핑장은_담을_수_없고_같은_캠핑장은_한_번만_담긴다() {
        Long id = defaultListId("me");
        assertThrows(NotFoundException.class, () -> service.addCamp("me", id, "없는id"));

        service.addCamp("me", id, "c1");
        service.addCamp("me", id, "c1");
        assertEquals(1, service.findLists("me").get(0).campCount());
    }

    @Test
    void 비교표는_가장_좋은_값을_표시하고_더미임을_알린다() {
        Long id = defaultListId("me");
        service.addCamp("me", id, "c1");
        service.addCamp("me", id, "c2");
        service.addCamp("me", id, "c3");

        CompareResponse result = service.compare("me", id);
        assertTrue(result.dummy());
        assertEquals(6, result.rows().size());

        // 응급실: 햇살글램핑(차로 9분)이 가장 가깝다
        List<CompareResponse.Cell> er = result.rows().get(1).cells();
        assertEquals(List.of(false, false, true), er.stream().map(CompareResponse.Cell::best).toList());
        // 대피소: 걸어서 가는 곳 중 가장 짧은 설하캠핑장 (차로 4분보다 먼저 친다)
        List<CompareResponse.Cell> shelter = result.rows().get(3).cells();
        assertEquals(List.of(true, false, false), shelter.stream().map(CompareResponse.Cell::best).toList());
        // 막대: 응급실은 분 숫자로 그리고, 대피소는 걸어서·차로가 섞여 막대를 그리지 않는다
        assertEquals(List.of(17, 21, 9), er.stream().map(CompareResponse.Cell::value).toList());
        assertTrue(shelter.stream().allMatch(c -> c.value() == null));
        // 안전점수: 가장 높은 설하캠핑장(90)에 칩
        assertEquals("가장 높음", result.rows().get(0).bestLabel());
        assertEquals(List.of(true, false, false),
                result.rows().get(0).cells().stream().map(CompareResponse.Cell::best).toList());
    }

    @Test
    void 캠핑장이_한_곳이면_가장_좋은_값을_표시하지_않는다() {
        Long id = defaultListId("me");
        service.addCamp("me", id, "c1");

        CompareResponse result = service.compare("me", id);
        assertFalse(result.rows().stream().flatMap(r -> r.cells().stream()).anyMatch(CompareResponse.Cell::best));
    }
}
