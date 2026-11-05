package com.campin.family;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.campin.common.NotFoundException;

import lombok.RequiredArgsConstructor;

// 아이 건강 응급 카드 (가족방 2차). 건강 정보라서 "내가 들어가 있는 방"의 카드만 다룬다
@Service                   // 스프링: 비즈니스 로직 담당 빈으로 등록
@RequiredArgsConstructor   // Lombok: final 필드를 받는 생성자를 만든다 → 스프링이 그 생성자로 주입
public class ChildCardService {

    private final ChildCardMapper childCardMapper;
    private final FamilyService familyService;

    public List<ChildCardResponse> findAll(String loginId) {
        MyRoom my = familyService.requireMyRoom(loginId);
        return childCardMapper.findAll(my.roomId()).stream().map(ChildCardResponse::from).toList();
    }

    public ChildCardResponse add(String loginId, ChildCardRequest req) {
        MyRoom my = familyService.requireMyRoom(loginId);
        ChildCard card = toCard(req, my);
        childCardMapper.insert(card);
        card.setUpdatedAt(OffsetDateTime.now()); // 화면 표시용 (DB 에는 now() 로 들어가 거의 같은 값)
        return ChildCardResponse.from(card);
    }

    public ChildCardResponse update(String loginId, Long cardId, ChildCardRequest req) {
        MyRoom my = familyService.requireMyRoom(loginId);
        ChildCard card = toCard(req, my);
        card.setId(cardId);
        // 다른 가족방 카드이거나 없는 카드면 0줄 → 없는 것처럼 404
        if (childCardMapper.update(card) == 0) {
            throw new NotFoundException("아이 카드를 찾을 수 없습니다.");
        }
        card.setUpdatedAt(OffsetDateTime.now());
        return ChildCardResponse.from(card);
    }

    public void delete(String loginId, Long cardId) {
        MyRoom my = familyService.requireMyRoom(loginId);
        if (childCardMapper.delete(my.roomId(), cardId) == 0) {
            throw new NotFoundException("아이 카드를 찾을 수 없습니다.");
        }
    }

    private static ChildCard toCard(ChildCardRequest req, MyRoom my) {
        ChildCard card = new ChildCard();
        card.setFamilyRoomId(my.roomId());
        card.setName(req.name().trim());
        card.setBirthDate(req.birthDate());
        card.setBloodType(blankToNull(req.bloodType()));
        card.setAllergy(blankToNull(req.allergy()));
        card.setDisease(blankToNull(req.disease()));
        card.setMedicine(blankToNull(req.medicine()));
        card.setMemo(blankToNull(req.memo()));
        card.setUpdatedBy(my.memberId());
        card.setUpdatedByName(my.memberName());
        return card;
    }

    // 비워 둔 칸은 빈 글자 대신 null 로 저장한다 → 화면에서 "없음"과 "모름"을 헷갈리지 않게
    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
