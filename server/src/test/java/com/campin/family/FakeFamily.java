package com.campin.family;

import java.util.List;
import java.util.Map;

import com.campin.member.Member;
import com.campin.member.MemberMapper;

// 아이 카드·일정 테스트용: DB 없이 "누가 어느 가족방에 있는지"만 정해 둔 FamilyService 를 만든다
// mom·dad 는 1번 방, other 는 2번 방, alone 은 방 없음
final class FakeFamily {

    static final Map<String, Member> MEMBERS = Map.of(
            "mom", member(1L, "mom", "엄마"),
            "dad", member(2L, "dad", "아빠"),
            "other", member(3L, "other", "남"),
            "alone", member(4L, "alone", "혼자"));
    private static final Map<Long, Long> ROOM_OF = Map.of(1L, 1L, 2L, 1L, 3L, 2L);

    private FakeFamily() {
    }

    static FamilyService service() {
        MemberMapper memberMapper = new MemberMapper() {
            @Override public Member findByLoginId(String loginId) { return MEMBERS.get(loginId); }
            @Override public boolean existsByLoginId(String loginId) { return MEMBERS.containsKey(loginId); }
            @Override public void insert(Member member) { }
        };
        FamilyMapper familyMapper = new FamilyMapper() {
            @Override public FamilyRoom findRoomByMember(Long memberId) {
                Long roomId = ROOM_OF.get(memberId);
                if (roomId == null) return null;
                FamilyRoom r = new FamilyRoom();
                r.setId(roomId);
                return r;
            }
            // 아래는 아이 카드·일정 테스트에서 쓰지 않는다
            @Override public FamilyRoom findRoomByInviteCode(String inviteCode) { return null; }
            @Override public List<Member> findMembers(Long roomId) { return List.of(); }
            @Override public int insertRoom(FamilyRoom room) { return 0; }
            @Override public int insertMember(Long roomId, Long memberId) { return 0; }
            @Override public int deleteMember(Long memberId) { return 0; }
            @Override public int deleteRoomIfEmpty(Long roomId) { return 0; }
            @Override public List<MeetingPlace> findPlaces(Long roomId) { return List.of(); }
            @Override public int insertPlace(MeetingPlace place) { return 0; }
            @Override public int deletePlace(Long roomId, Long placeId) { return 0; }
        };
        return new FamilyService(familyMapper, memberMapper);
    }

    private static Member member(Long id, String loginId, String name) {
        Member m = new Member();
        m.setId(id);
        m.setLoginId(loginId);
        m.setName(name);
        return m;
    }
}
