package com.campin.mycamping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;

// DB · 디스크 없이 후기 규칙만 확인한다 (회원 두 명 + 메모리 일정 · 후기 · 사진)
class CampReviewServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 1, 2, 3};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final List<CampPlan> plans = new ArrayList<>();
    private final FakeCampReviewMapper reviews = new FakeCampReviewMapper();
    private final FakeReviewPhotoStore store = new FakeReviewPhotoStore();
    private CampReviewService service;

    @BeforeEach
    void setUp() {
        MemberMapper members = new MemberMapper() {
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
        CampPlanMapper planMapper = new CampPlanMapper() {
            @Override public List<CampPlan> findByMemberId(Long memberId) { return List.of(); }
            @Override public CampPlan findById(Long memberId, Long planId) {
                return plans.stream().filter(p -> p.getId().equals(planId) && p.getMemberId().equals(memberId))
                        .findFirst().orElse(null);
            }
            @Override public boolean exists(Long memberId, Long campId, LocalDate checkIn) { return false; }
            @Override public boolean existsSpot(Long memberId, double lat, double lng, LocalDate checkIn) { return false; }
            @Override public int insert(CampPlan plan) { return 1; }
            @Override public int update(CampPlan plan) { return 1; }
            @Override public int delete(Long memberId, Long planId) { return 0; }
        };
        service = new CampReviewService(members, planMapper, reviews, store);
        plan(1L, 1L, TODAY.minusDays(1));   // 어제 끝난 캠핑 (camper01)
        plan(2L, 1L, TODAY);                // 오늘 체크아웃 — 아직 캠핑 중
        plan(3L, 2L, TODAY.minusDays(3));   // camper02 의 지난 캠핑
    }

    private void plan(Long id, Long memberId, LocalDate checkOut) {
        CampPlan p = new CampPlan();
        p.setId(id);
        p.setMemberId(memberId);
        p.setCheckIn(checkOut.minusDays(2));
        p.setCheckOut(checkOut);
        plans.add(p);
    }

    private static MultipartFile file(byte[] bytes) {
        return new MockMultipartFile("photos", "camp.jpg", "image/jpeg", bytes);
    }

    private CampReviewResponse save(String loginId, Long planId, String content, List<Long> keep, List<MultipartFile> files) {
        return service.save(loginId, planId, content, keep, files, TODAY);
    }

    @Test
    void 글만_써도_저장되고_내_후기_목록에_보인다() {
        CampReviewResponse r = save("camper01", 1L, "  물소리가 좋았어요  ", null, null);

        assertEquals("물소리가 좋았어요", r.content());   // 앞뒤 공백은 뺀다
        assertTrue(r.photos().isEmpty());
        List<CampReviewResponse> mine = service.findAll("camper01");
        assertEquals(1, mine.size());
        assertEquals(1L, mine.get(0).planId());
        assertTrue(service.findAll("camper02").isEmpty());
    }

    @Test
    void 사진만_올려도_저장되고_파일은_무작위_이름으로_둔다() {
        CampReviewResponse r = save("camper01", 1L, "", null, List.of(file(JPG), file(PNG)));

        assertNull(r.content());
        assertEquals(2, r.photos().size());
        assertEquals(2, store.files.size());
        assertTrue(store.files.keySet().stream().allMatch(n -> n.matches("[0-9a-f-]{36}\\.(jpg|png)")));
        assertEquals("/api/member/camp-plans/1/review/photos/" + r.photos().get(0).id(), r.photos().get(0).url());
    }

    @Test
    void 글도_사진도_없으면_400() {
        assertThrows(InvalidRequestException.class, () -> save("camper01", 1L, "   ", List.of(), List.of()));
    }

    @Test
    void 체크아웃이_지나야_쓸_수_있다() {
        assertThrows(InvalidRequestException.class, () -> save("camper01", 2L, "아직 캠핑 중", null, null));
    }

    @Test
    void 남의_일정에는_쓰지도_사진을_보지도_못한다() {
        assertThrows(NotFoundException.class, () -> save("camper02", 1L, "남의 일정", null, null));

        Long photoId = save("camper01", 1L, null, null, List.of(file(JPG))).photos().get(0).id();
        assertThrows(NotFoundException.class, () -> service.loadPhoto("camper02", 1L, photoId));
        assertThrows(NotFoundException.class, () -> service.loadPhoto("camper02", 3L, photoId));
        assertEquals("image/jpeg", service.loadPhoto("camper01", 1L, photoId).type().toString());
    }

    @Test
    void 글은_1000자까지() {
        save("camper01", 1L, "가".repeat(1000), null, null);
        assertThrows(InvalidRequestException.class, () -> save("camper01", 1L, "가".repeat(1001), null, null));
    }

    @Test
    void 사진은_9장까지() {
        List<MultipartFile> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) ten.add(file(JPG));
        assertThrows(InvalidRequestException.class, () -> save("camper01", 1L, "글", null, ten));
        assertTrue(store.files.isEmpty());   // 아무것도 저장하지 않는다
    }

    @Test
    void 이미지가_아닌_파일은_이름이_jpg여도_막는다() {
        MultipartFile html = new MockMultipartFile("photos", "camp.jpg", "image/jpeg", "<html>".getBytes());
        assertThrows(InvalidRequestException.class, () -> save("camper01", 1L, "글", null, List.of(file(JPG), html)));
        assertTrue(store.files.isEmpty());
        assertTrue(reviews.reviews.isEmpty());
    }

    @Test
    void 수정하면_뺀_사진은_파일까지_지우고_남긴_사진_다음에_새_사진이_온다() {
        List<CampReviewResponse.Photo> first = save("camper01", 1L, "처음", null, List.of(file(JPG), file(JPG), file(JPG))).photos();
        Long a = first.get(0).id(), b = first.get(1).id(), c = first.get(2).id();
        String bFile = reviews.photos.stream().filter(p -> p.getId().equals(b)).findFirst().get().getFileName();

        // b 를 빼고, c 를 앞으로, 새 사진 하나 추가
        CampReviewResponse r = save("camper01", 1L, "고친 글", List.of(c, a), List.of(file(PNG)));

        assertEquals("고친 글", r.content());
        List<Long> ids = r.photos().stream().map(CampReviewResponse.Photo::id).toList();
        assertEquals(3, ids.size());
        assertEquals(List.of(c, a), ids.subList(0, 2));
        assertFalse(ids.contains(b));
        assertFalse(store.files.containsKey(bFile));
        assertEquals(3, store.files.size());
        assertEquals(1, reviews.reviews.size());   // 일정 하나에 후기 하나
    }

    @Test
    void 다른_후기의_사진_id는_남길_수_없다() {
        Long other = save("camper02", 3L, null, null, List.of(file(JPG))).photos().get(0).id();
        assertThrows(InvalidRequestException.class, () -> save("camper01", 1L, "글", List.of(other), null));
    }

    @Test
    void 저장_중_실패하면_새로_쓴_파일을_남기지_않는다() {
        store.failOnSave = true;
        assertThrows(UncheckedIOException.class, () -> save("camper01", 1L, "글", null, List.of(file(JPG))));
        assertTrue(store.files.isEmpty());
    }
}
