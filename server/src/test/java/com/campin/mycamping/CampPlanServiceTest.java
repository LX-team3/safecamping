package com.campin.mycamping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;

// DB 없이 내 캠핑 규칙만 확인한다 (회원 두 명 + 메모리 표)
class CampPlanServiceTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private final List<CampPlan> table = new ArrayList<>();
    private final FakeCampReviewMapper reviews = new FakeCampReviewMapper();
    private final FakeReviewPhotoStore photoStore = new FakeReviewPhotoStore();
    private CampPlanService service;

    @BeforeEach
    void setUp() {
        MemberMapper fakeMembers = new MemberMapper() {
            @Override public Member findByLoginId(String loginId) {
                Member m = new Member();
                if ("camper01".equals(loginId)) m.setId(1L);
                else if ("camper02".equals(loginId)) m.setId(2L);
                else return null;
                m.setLoginId(loginId);
                return m;
            }
            @Override public boolean existsByLoginId(String loginId) { return false; }
            @Override public void insert(Member m) { }
        };
        CampPlanMapper fakePlans = new CampPlanMapper() {
            @Override public List<CampPlan> findByMemberId(Long memberId) {
                return table.stream().filter(p -> p.getMemberId().equals(memberId))
                        .sorted(Comparator.comparing(CampPlan::getCheckIn)).toList();  // SQL 의 ORDER BY check_in
            }
            @Override public CampPlan findById(Long memberId, Long planId) {
                return table.stream().filter(p -> p.getId().equals(planId) && p.getMemberId().equals(memberId))
                        .findFirst().orElse(null);
            }
            @Override public boolean exists(Long memberId, Long campId, LocalDate checkIn) {
                return table.stream().anyMatch(p -> p.getMemberId().equals(memberId)
                        && campId.equals(p.getCampId()) && p.getCheckIn().equals(checkIn));
            }
            @Override public boolean existsSpot(Long memberId, double lat, double lng, LocalDate checkIn) {
                return table.stream().anyMatch(p -> p.getMemberId().equals(memberId) && p.getCampId() == null
                        && p.getLat() == lat && p.getLng() == lng && p.getCheckIn().equals(checkIn));
            }
            @Override public int insert(CampPlan plan) {
                plan.setId((long) table.size() + 1);
                table.add(plan);
                return 1;
            }
            @Override public int delete(Long memberId, Long planId) {
                boolean removed = table.removeIf(p -> p.getId().equals(planId) && p.getMemberId().equals(memberId));
                if (removed) reviews.cascadeDeletePlan(planId);  // DB 의 ON DELETE CASCADE 흉내
                return removed ? 1 : 0;
            }
        };
        service = new CampPlanService(fakeMembers, fakePlans, reviews, photoStore);
    }

    // 오늘에서 며칠 뒤 체크인, nights 박
    private static CampPlanRequest plan(long campId, int daysLater, int nights) {
        LocalDate in = TODAY.plusDays(daysLater);
        return new CampPlanRequest(campId, " 설하캠핑장 ", "강원 인제군 북면", null, null, in, in.plusDays(nights), "  ");
    }

    // 캠핑장이 아닌 자리 (차박 후보지 · 찍은 자리): campId 없이 좌표
    private static CampPlanRequest spot(Double lat, Double lng, int daysLater) {
        LocalDate in = TODAY.plusDays(daysLater);
        return new CampPlanRequest(null, "475-3 잡종지", "강원 인제군", lat, lng, in, in.plusDays(1), null);
    }

    @Test
    void 등록하면_체크인_빠른_순으로_보인다() {
        service.add("camper01", plan(2L, 20, 1));
        CampPlanResponse first = service.add("camper01", plan(1L, 5, 2));

        assertEquals("설하캠핑장", first.campName());  // 앞뒤 공백은 지운다
        assertNull(first.memo());                      // 빈 메모는 null
        assertEquals(List.of(1L, 2L), service.findAll("camper01").stream().map(CampPlanResponse::campId).toList());
    }

    @Test
    void 오늘_체크인은_되고_지난_날짜는_안_된다() {
        service.add("camper01", plan(1L, 0, 1));
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", plan(2L, -1, 1)));
    }

    @Test
    void 체크아웃은_체크인_다음_날부터() {
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", plan(1L, 3, 0)));
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", plan(1L, 3, -1)));
        assertTrue(service.findAll("camper01").isEmpty());
    }

    @Test
    void 같은_캠핑장_같은_체크인은_두_번_등록되지_않는다() {
        service.add("camper01", plan(1L, 5, 1));
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", plan(1L, 5, 3)));
        service.add("camper02", plan(1L, 5, 1));  // 다른 회원은 따로
        assertEquals(1, service.findAll("camper01").size());
    }

    @Test
    void 캠핑장이_아닌_자리도_좌표로_등록된다() {
        CampPlanResponse saved = service.add("camper01", spot(38.0570688, 128.1661291, 5));

        assertNull(saved.campId());                  // 캠핑장 번호 없음
        assertEquals(38.057069, saved.lat());        // 소수 6자리로 맞춰 저장
        assertEquals(128.166129, saved.lng());
        assertEquals("475-3 잡종지", saved.campName());
        assertEquals(1, service.findAll("camper01").size());
    }

    @Test
    void 캠핑장도_좌표도_없으면_400() {
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", spot(null, null, 5)));
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", spot(38.05, null, 5)));
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", spot(95.0, 128.1, 5)));
        assertTrue(service.findAll("camper01").isEmpty());
    }

    @Test
    void 같은_자리_같은_체크인은_두_번_등록되지_않는다() {
        service.add("camper01", spot(38.0570688, 128.1661291, 5));
        // 끝자리만 다른 같은 자리 (소수 6자리로 맞추면 같음)
        assertThrows(InvalidRequestException.class, () -> service.add("camper01", spot(38.0570691, 128.1661288, 5)));
        service.add("camper01", spot(38.0570688, 128.1661291, 6));   // 체크인이 다르면 가능
        service.add("camper02", spot(38.0570688, 128.1661291, 5));   // 다른 회원도 가능
        assertEquals(2, service.findAll("camper01").size());
    }

    @Test
    void 남의_등록은_보이지도_지우지도_못한다() {
        Long id = service.add("camper01", plan(1L, 5, 1)).id();

        assertTrue(service.findAll("camper02").isEmpty());
        assertThrows(NotFoundException.class, () -> service.delete("camper02", id));

        service.delete("camper01", id);
        assertTrue(service.findAll("camper01").isEmpty());
    }

    @Test
    void 일정을_지우면_후기_사진_파일도_지운다() {
        Long id = service.add("camper01", plan(1L, 5, 1)).id();
        CampReview r = new CampReview();
        r.setPlanId(id);
        r.setMemberId(1L);
        reviews.insert(r);
        CampReviewPhoto photo = new CampReviewPhoto();
        photo.setReviewId(r.getId());
        photo.setFileName("a.jpg");
        photo.setSortOrder(0);
        reviews.insertPhoto(photo);
        photoStore.files.put("a.jpg", new byte[] {1});

        assertThrows(NotFoundException.class, () -> service.delete("camper02", id));
        assertTrue(photoStore.files.containsKey("a.jpg"));  // 남의 요청으로는 파일도 그대로

        service.delete("camper01", id);
        assertTrue(photoStore.files.isEmpty());
        assertTrue(reviews.reviews.isEmpty());
    }
}
