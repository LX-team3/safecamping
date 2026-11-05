package com.campin.family;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;

import lombok.RequiredArgsConstructor;

// 가족 모임방 (내 캠핑 탭). 1차: 방 만들기·초대코드 입장·나가기·모일 장소
// 위치·아이 건강 정보를 다루게 되므로 모든 기능은 "내가 들어가 있는 방"에서만 동작한다
// (방 번호를 주소로 받지 않고 로그인한 회원의 방을 찾아서 쓴다 → 남의 방에 접근할 길이 없다)
@Service                   // 스프링: 비즈니스 로직 담당 빈으로 등록
@RequiredArgsConstructor   // Lombok: final 필드를 받는 생성자를 만든다 → 스프링이 그 생성자로 주입
public class FamilyService {

    // 헷갈리는 글자(0·O, 1·I·L)는 빼서 가족이 말로 불러 줄 때 틀리지 않게 한다
    static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 6;
    private static final int CODE_RETRY = 5;  // 31^6 ≈ 8억 개라 겹칠 일은 거의 없지만 혹시 겹치면 다시 만든다

    private final SecureRandom random = new SecureRandom();  // 코드를 짐작하기 어렵게 보안용 난수를 쓴다
    private final FamilyMapper familyMapper;
    private final MemberMapper memberMapper;

    // 내 가족방. 없으면 null → 화면은 "만들기 / 초대코드로 들어가기" 를 보여 준다
    public FamilyRoomResponse findMyRoom(String loginId) {
        Long memberId = memberId(loginId);
        FamilyRoom room = familyMapper.findRoomByMember(memberId);
        return room == null ? null : toResponse(room, memberId);
    }

    @Transactional  // 스프링: 방 만들기와 구성원 넣기를 하나로 묶는다 (중간에 실패하면 방도 안 생김)
    public FamilyRoomResponse createRoom(String loginId, String name) {
        Long memberId = memberId(loginId);
        ensureNoRoom(memberId);

        FamilyRoom room = new FamilyRoom();
        room.setName(name.trim());
        for (int i = 0; i < CODE_RETRY; i++) {
            room.setInviteCode(newInviteCode());
            if (familyMapper.insertRoom(room) == 1) {
                joinOrFail(room.getId(), memberId);
                return toResponse(room, memberId);
            }
        }
        throw new IllegalStateException("초대코드를 만들지 못했습니다.");  // 500 (사실상 일어나지 않음)
    }

    public FamilyRoomResponse join(String loginId, String inviteCode) {
        Long memberId = memberId(loginId);
        ensureNoRoom(memberId);

        // 가족이 소문자나 띄어쓰기를 섞어 입력해도 받아 준다 (예: "ab3 k7q")
        String code = inviteCode.replaceAll("\\s", "").toUpperCase();
        FamilyRoom room = familyMapper.findRoomByInviteCode(code);
        if (room == null) {
            throw new InvalidRequestException("초대코드를 다시 확인해 주세요.");
        }
        joinOrFail(room.getId(), memberId);
        return toResponse(room, memberId);
    }

    // 나가기. 마지막 사람이 나가면 방과 모일 장소도 지운다
    @Transactional  // 스프링: 나가기와 빈 방 지우기를 하나로 묶는다
    public void leave(String loginId) {
        Long memberId = memberId(loginId);
        FamilyRoom room = myRoom(memberId);
        familyMapper.deleteMember(memberId);
        familyMapper.deleteRoomIfEmpty(room.getId());
    }

    public List<MeetingPlaceResponse> findPlaces(String loginId) {
        FamilyRoom room = myRoom(memberId(loginId));
        return familyMapper.findPlaces(room.getId()).stream().map(MeetingPlaceResponse::from).toList();
    }

    public MeetingPlaceResponse addPlace(String loginId, MeetingPlaceRequest req) {
        Member me = member(loginId);
        FamilyRoom room = myRoom(me.getId());
        if ((req.lon() == null) != (req.lat() == null)) {
            throw new InvalidRequestException("위치 값이 올바르지 않습니다.");
        }

        MeetingPlace place = new MeetingPlace();
        place.setFamilyRoomId(room.getId());
        place.setName(req.name().trim());
        // 설명을 비워 두면 빈 글자 대신 null 로 저장한다
        place.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        place.setLon(req.lon());
        place.setLat(req.lat());
        place.setCreatedBy(me.getId());
        familyMapper.insertPlace(place);
        place.setCreatedByName(me.getName());
        return MeetingPlaceResponse.from(place);
    }

    public void deletePlace(String loginId, Long placeId) {
        FamilyRoom room = myRoom(memberId(loginId));
        if (familyMapper.deletePlace(room.getId(), placeId) == 0) {
            throw new NotFoundException("모일 장소를 찾을 수 없습니다.");
        }
    }

    private FamilyRoomResponse toResponse(FamilyRoom room, Long memberId) {
        List<FamilyRoomResponse.Person> members = familyMapper.findMembers(room.getId()).stream()
                .map(m -> new FamilyRoomResponse.Person(m.getName(), m.getId().equals(memberId)))
                .toList();
        return new FamilyRoomResponse(room.getId(), room.getName(), room.getInviteCode(), members);
    }

    private void ensureNoRoom(Long memberId) {
        if (familyMapper.findRoomByMember(memberId) != null) {
            throw new InvalidRequestException("이미 들어가 있는 가족방이 있습니다.");
        }
    }

    // 위 확인과 INSERT 사이에 다른 요청으로 먼저 들어갔으면 0줄 → 같은 문구로 알린다
    private void joinOrFail(Long roomId, Long memberId) {
        if (familyMapper.insertMember(roomId, memberId) == 0) {
            throw new InvalidRequestException("이미 들어가 있는 가족방이 있습니다.");
        }
    }

    // 아이 카드·일정 서비스가 "로그인한 회원 + 그 회원의 방"을 한 번에 얻을 때 쓴다 (방이 없으면 404)
    public MyRoom requireMyRoom(String loginId) {
        Member me = member(loginId);
        return new MyRoom(me.getId(), me.getName(), myRoom(me.getId()).getId());
    }

    // 가족방이 없는데 모일 장소 등을 부르면 404
    private FamilyRoom myRoom(Long memberId) {
        FamilyRoom room = familyMapper.findRoomByMember(memberId);
        if (room == null) {
            throw new NotFoundException("가족방이 없습니다.");
        }
        return room;
    }

    private String newInviteCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private Long memberId(String loginId) {
        return member(loginId).getId();
    }

    // 로그인 정보에는 아이디(loginId)만 있으므로 회원 번호·이름을 찾아서 쓴다
    private Member member(String loginId) {
        Member member = memberMapper.findByLoginId(loginId);
        if (member == null) {
            throw new NotFoundException("회원 정보를 찾을 수 없습니다.");
        }
        return member;
    }
}
