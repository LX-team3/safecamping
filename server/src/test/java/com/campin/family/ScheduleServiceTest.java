package com.campin.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.NotFoundException;

// DB 없이 일정 규칙만 확인한다 (mapper 는 메모리 List 로 대신함, 가족방은 FakeFamily)
class ScheduleServiceTest {

    private final List<FamilySchedule> schedules = new ArrayList<>();
    private ScheduleService service;

    @BeforeEach
    void setUp() {
        ScheduleMapper fakeMapper = new ScheduleMapper() {
            @Override public List<FamilySchedule> findAll(Long roomId) {
                return schedules.stream().filter(s -> s.getFamilyRoomId().equals(roomId))
                        .sorted(Comparator.comparing(FamilySchedule::getStartAt)).toList();  // SQL 의 ORDER BY start_at
            }
            @Override public int insert(FamilySchedule schedule) {
                schedule.setId((long) schedules.size() + 1);
                schedules.add(schedule);
                return 1;
            }
            @Override public int delete(Long roomId, Long scheduleId) {
                return schedules.removeIf(s -> s.getId().equals(scheduleId) && s.getFamilyRoomId().equals(roomId)) ? 1 : 0;
            }
        };
        service = new ScheduleService(fakeMapper, FakeFamily.service());
    }

    private static ScheduleRequest schedule(String title, String startAt) {
        return new ScheduleRequest(title, OffsetDateTime.parse(startAt), "");
    }

    @Test
    void 일정은_같은_가족방에서_시간_순으로_보인다() {
        service.add("mom", schedule("집 가는 날 체크아웃", "2026-11-15T11:00:00+09:00"));
        ScheduleResponse first = service.add("dad", schedule(" 출발 ", "2026-11-14T06:00:00+09:00"));

        assertEquals("출발", first.title());
        assertNull(first.memo());  // 빈 메모는 null
        assertEquals("아빠", first.createdByName());
        assertEquals(List.of("출발", "집 가는 날 체크아웃"),
                service.findAll("mom").stream().map(ScheduleResponse::title).toList());
    }

    @Test
    void 다른_가족방의_일정은_보이지도_지우지도_못한다() {
        Long id = service.add("mom", schedule("출발", "2026-11-14T06:00:00+09:00")).id();

        assertTrue(service.findAll("other").isEmpty());
        assertThrows(NotFoundException.class, () -> service.delete("other", id));

        service.delete("dad", id);
        assertTrue(service.findAll("mom").isEmpty());
    }

    @Test
    void 가족방이_없으면_일정을_쓸_수_없다() {
        assertThrows(NotFoundException.class, () -> service.findAll("alone"));
    }
}
