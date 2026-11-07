package com.campin.trip;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.campin.common.InvalidRequestException;

// 누구와, 언제: 인원·날짜 검사 → 몇 박인지
// 화면에서도 막지만 서버에서 한 번 더 검사한다 (주소로 직접 보내는 요청 대비)
@Service
public class TripService {

    public static final Set<String> AGE_GROUPS = Set.of("infant", "preschool", "elementary");
    static final int MAX_PEOPLE = 10; // 화면 −/+ 버튼 한도와 같게 (안전 기준이 아니라 입력 실수 방지용)

    private final Clock clock; // "오늘"을 정하는 시계. 테스트에서는 날짜를 고정한 시계를 넣는다

    // @Autowired: 스프링은 이 생성자로 만든다 (한국 시간 기준 오늘)
    @Autowired
    public TripService() {
        this(Clock.system(ZoneId.of("Asia/Seoul")));
    }

    TripService(Clock clock) {
        this.clock = clock;
    }

    public TripCheckResponse check(TripCheckRequest req) {
        if (req.checkIn() == null || req.checkOut() == null) {
            throw new InvalidRequestException("체크인과 체크아웃 날짜를 골라 주세요.");
        }
        if (req.checkIn().isBefore(LocalDate.now(clock))) {
            throw new InvalidRequestException("지난 날짜는 고를 수 없어요.");
        }
        if (!req.checkOut().isAfter(req.checkIn())) {
            throw new InvalidRequestException("체크아웃은 체크인 다음 날부터 고를 수 있어요.");
        }

        int adults = req.adults() == null ? 0 : req.adults();
        int kids = req.kids() == null ? 0 : req.kids();
        if (adults < 1) {
            throw new InvalidRequestException("어른이 한 명 이상 있어야 해요.");
        }
        if (adults > MAX_PEOPLE || kids < 0 || kids > MAX_PEOPLE) {
            throw new InvalidRequestException("인원은 " + MAX_PEOPLE + "명까지 고를 수 있어요.");
        }

        List<String> ages = req.childAges() == null ? List.of() : req.childAges();
        if (ages.size() != kids) {
            throw new InvalidRequestException("아이마다 나이대를 골라 주세요.");
        }
        if (!AGE_GROUPS.containsAll(ages)) {
            throw new InvalidRequestException("없는 나이대입니다.");
        }

        long nights = ChronoUnit.DAYS.between(req.checkIn(), req.checkOut());
        return new TripCheckResponse(req.checkIn(), req.checkOut(), nights, adults, kids, ages);
    }
}
