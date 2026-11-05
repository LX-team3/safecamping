package com.campin.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

// DB 없이 가족방 규칙만 확인한다 (mapper 는 메모리 List·Map 으로 대신함)
class FamilyServiceTest {

    private final Map<String, Member> members = new HashMap<>();
    private final List<FamilyRoom> rooms = new ArrayList<>();
    private final Map<Long, Long> roomOf = new HashMap<>();  // 회원 번호 → 방 번호 (family_member)
    private final List<MeetingPlace> places = new ArrayList<>();
    private FamilyService service;

    @BeforeEach
    void setUp() {
        members.put("mom", member(1L, "mom", "엄마"));
        members.put("dad", member(2L, "dad", "아빠"));
        members.put("other", member(3L, "other", "남"));

        MemberMapper fakeMemberMapper = new MemberMapper() {
            @Override public Member findByLoginId(String loginId) { return members.get(loginId); }
            @Override public boolean existsByLoginId(String loginId) { return members.containsKey(loginId); }
            @Override public void insert(Member member) { }
        };

        FamilyMapper fakeFamilyMapper = new FamilyMapper() {
            @Override public FamilyRoom findRoomByMember(Long memberId) {
                Long roomId = roomOf.get(memberId);
                return roomId == null ? null : room(roomId);
            }
            @Override public FamilyRoom findRoomByInviteCode(String inviteCode) {
                return rooms.stream().filter(r -> r.getInviteCode().equals(inviteCode)).findFirst().orElse(null);
            }
            @Override public List<Member> findMembers(Long roomId) {
                return members.values().stream().filter(m -> roomId.equals(roomOf.get(m.getId())))
                        .sorted((a, b) -> a.getId().compareTo(b.getId())).toList();
            }
            @Override public int insertRoom(FamilyRoom room) {
                if (findRoomByInviteCode(room.getInviteCode()) != null) return 0;
                room.setId((long) rooms.size() + 1);
                rooms.add(room);
                return 1;
            }
            @Override public int insertMember(Long roomId, Long memberId) {
                if (roomOf.containsKey(memberId)) return 0;
                roomOf.put(memberId, roomId);
                return 1;
            }
            @Override public int deleteMember(Long memberId) {
                return roomOf.remove(memberId) == null ? 0 : 1;
            }
            @Override public int deleteRoomIfEmpty(Long roomId) {
                if (roomOf.containsValue(roomId)) return 0;
                places.removeIf(p -> p.getFamilyRoomId().equals(roomId));  // ON DELETE CASCADE 흉내
                return rooms.removeIf(r -> r.getId().equals(roomId)) ? 1 : 0;
            }
            @Override public List<MeetingPlace> findPlaces(Long roomId) {
                return places.stream().filter(p -> p.getFamilyRoomId().equals(roomId)).toList();
            }
            @Override public int insertPlace(MeetingPlace place) {
                place.setId((long) places.size() + 1);
                places.add(place);
                return 1;
            }
            @Override public int deletePlace(Long roomId, Long placeId) {
                return places.removeIf(p -> p.getId().equals(placeId) && p.getFamilyRoomId().equals(roomId)) ? 1 : 0;
            }
            private FamilyRoom room(Long id) {
                return rooms.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
            }
        };
        service = new FamilyService(fakeFamilyMapper, fakeMemberMapper);
    }

    private static Member member(Long id, String loginId, String name) {
        Member m = new Member();
        m.setId(id);
        m.setLoginId(loginId);
        m.setName(name);
        return m;
    }

    private static MeetingPlaceRequest place(String name) {
        return new MeetingPlaceRequest(name, null, null, null);
    }

    @Test
    void 가족방이_없으면_null() {
        assertNull(service.findMyRoom("mom"));
    }

    @Test
    void 방을_만들면_헷갈리지_않는_글자로_된_6자리_초대코드가_생기고_나도_구성원이_된다() {
        FamilyRoomResponse room = service.createRoom("mom", " 우리 가족 ");

        assertEquals("우리 가족", room.name());
        assertEquals(6, room.inviteCode().length());
        assertTrue(room.inviteCode().chars().allMatch(c -> FamilyService.CODE_CHARS.indexOf(c) >= 0));
        assertEquals(List.of(new FamilyRoomResponse.Person("엄마", true)), room.members());
    }

    @Test
    void 초대코드는_소문자_띄어쓰기를_섞어도_들어갈_수_있다() {
        String code = service.createRoom("mom", "우리 가족").inviteCode();
        String messy = code.substring(0, 3).toLowerCase() + " " + code.substring(3);

        FamilyRoomResponse room = service.join("dad", messy);
        assertEquals(2, room.members().size());
        assertEquals(new FamilyRoomResponse.Person("아빠", true), room.members().get(1));
    }

    @Test
    void 틀린_초대코드는_400() {
        assertThrows(InvalidRequestException.class, () -> service.join("dad", "ZZZZZZ"));
    }

    @Test
    void 한_사람은_가족방_하나에만_들어갈_수_있다() {
        String code = service.createRoom("mom", "우리 가족").inviteCode();
        service.createRoom("dad", "아빠네");

        assertThrows(InvalidRequestException.class, () -> service.createRoom("mom", "또 만들기"));
        assertThrows(InvalidRequestException.class, () -> service.join("dad", code));
    }

    @Test
    void 모일_장소는_같은_방_가족만_보고_지울_수_있다() {
        String code = service.createRoom("mom", "우리 가족").inviteCode();
        service.join("dad", code);
        MeetingPlaceResponse added = service.addPlace("mom", new MeetingPlaceRequest(
                " 관리동 앞 주차장 ", "  ", 127.5, 37.7));

        assertEquals("관리동 앞 주차장", added.name());
        assertNull(added.description());  // 빈 설명은 null
        assertEquals("엄마", added.createdByName());
        assertEquals(1, service.findPlaces("dad").size());  // 같은 방 아빠도 본다

        service.createRoom("other", "남의 방");
        assertTrue(service.findPlaces("other").isEmpty());  // 다른 방에서는 안 보인다
        assertThrows(NotFoundException.class, () -> service.deletePlace("other", added.id()));

        service.deletePlace("dad", added.id());
        assertTrue(service.findPlaces("mom").isEmpty());
    }

    @Test
    void 가족방이_없으면_모일_장소를_쓸_수_없다() {
        assertThrows(NotFoundException.class, () -> service.findPlaces("mom"));
        assertThrows(NotFoundException.class, () -> service.addPlace("mom", place("주차장")));
    }

    @Test
    void 경도_위도는_둘_다_넣거나_둘_다_비운다() {
        service.createRoom("mom", "우리 가족");
        assertThrows(InvalidRequestException.class,
                () -> service.addPlace("mom", new MeetingPlaceRequest("주차장", null, 127.5, null)));
    }

    @Test
    void 마지막_사람이_나가면_방과_모일_장소도_지워진다() {
        String code = service.createRoom("mom", "우리 가족").inviteCode();
        service.join("dad", code);
        service.addPlace("mom", place("주차장"));

        service.leave("mom");
        assertNull(service.findMyRoom("mom"));
        assertEquals(1, service.findMyRoom("dad").members().size());  // 아빠가 남아 있으니 방은 그대로

        service.leave("dad");
        assertTrue(rooms.isEmpty());
        assertTrue(places.isEmpty());
    }
}
