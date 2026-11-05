package com.campin.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.NotFoundException;

// DB 없이 아이 카드 규칙만 확인한다 (mapper 는 메모리 List 로 대신함, 가족방은 FakeFamily)
class ChildCardServiceTest {

    private final List<ChildCard> cards = new ArrayList<>();
    private ChildCardService service;

    @BeforeEach
    void setUp() {
        ChildCardMapper fakeMapper = new ChildCardMapper() {
            @Override public List<ChildCard> findAll(Long roomId) {
                return cards.stream().filter(c -> c.getFamilyRoomId().equals(roomId)).toList();
            }
            @Override public int insert(ChildCard card) {
                card.setId((long) cards.size() + 1);
                cards.add(card);
                return 1;
            }
            @Override public int update(ChildCard card) {
                for (int i = 0; i < cards.size(); i++) {
                    ChildCard c = cards.get(i);
                    if (c.getId().equals(card.getId()) && c.getFamilyRoomId().equals(card.getFamilyRoomId())) {
                        cards.set(i, card);
                        return 1;
                    }
                }
                return 0;
            }
            @Override public int delete(Long roomId, Long cardId) {
                return cards.removeIf(c -> c.getId().equals(cardId) && c.getFamilyRoomId().equals(roomId)) ? 1 : 0;
            }
        };
        service = new ChildCardService(fakeMapper, FakeFamily.service());
    }

    private static ChildCardRequest card(String name, String allergy) {
        return new ChildCardRequest(name, LocalDate.of(2019, 5, 2), "A+", allergy, " ", null, null);  // 지병은 " " (비움)
    }

    @Test
    void 같은_가족방_사람은_카드를_함께_보고_빈_칸은_null_로_저장한다() {
        ChildCardResponse added = service.add("mom", card(" 서준 ", "땅콩"));

        assertEquals("서준", added.name());
        assertNull(added.disease());  // " " 처럼 비운 칸은 null
        assertEquals("엄마", added.updatedByName());
        assertEquals(1, service.findAll("dad").size());  // 같은 방 아빠도 본다
    }

    @Test
    void 다른_가족방의_카드는_보이지도_고치지도_지우지도_못한다() {
        Long id = service.add("mom", card("서준", "땅콩")).id();

        assertTrue(service.findAll("other").isEmpty());
        assertThrows(NotFoundException.class, () -> service.update("other", id, card("바꿈", null)));
        assertThrows(NotFoundException.class, () -> service.delete("other", id));
        assertEquals("땅콩", service.findAll("mom").get(0).allergy());  // 그대로
    }

    @Test
    void 수정하면_고친_사람이_바뀐다() {
        Long id = service.add("mom", card("서준", "땅콩")).id();
        ChildCardResponse updated = service.update("dad", id, card("서준", "땅콩, 새우"));

        assertEquals("땅콩, 새우", updated.allergy());
        assertEquals("아빠", updated.updatedByName());
    }

    @Test
    void 가족방이_없으면_카드를_쓸_수_없다() {
        assertThrows(NotFoundException.class, () -> service.findAll("alone"));
        assertThrows(NotFoundException.class, () -> service.add("alone", card("서준", null)));
    }
}
