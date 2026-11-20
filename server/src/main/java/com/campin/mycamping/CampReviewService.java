package com.campin.mycamping;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.campin.common.InvalidRequestException;
import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;

import lombok.RequiredArgsConstructor;

// 지난 캠핑 후기 (웹 내 예약 → 지난 캠핑 카드). 일정 하나에 후기 하나, 별점 없음
// 규칙: 체크아웃이 지난 일정만 · 글이나 사진 중 하나는 꼭 · 글 1000자 · 사진 9장 · 한 장 10MB · jpg·png·webp·gif
// 프론트(WebReviewModal)에서도 같은 검사를 하지만 서버에서 한 번 더 한다
@Service
@RequiredArgsConstructor
public class CampReviewService {

    static final int MAX_LEN = 1000;
    static final int MAX_PHOTOS = 9;
    static final long MAX_BYTES = 10L * 1024 * 1024;  // application.properties 의 max-file-size 와 같은 값
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");  // "오늘"은 한국 날짜 기준

    private final MemberMapper memberMapper;
    private final CampPlanMapper campPlanMapper;
    private final CampReviewMapper reviewMapper;
    private final ReviewPhotoStore store;

    // 사진 파일과 그 형식 (컨트롤러가 그대로 내려보낸다)
    public record PhotoFile(Resource resource, MediaType type) {
    }

    // 내 후기 전부. 사진은 한 번에 받아 후기별로 나눈다 (후기마다 SQL 을 부르지 않게)
    public List<CampReviewResponse> findAll(String loginId) {
        Long memberId = findMember(loginId).getId();
        Map<Long, List<CampReviewPhoto>> photos = reviewMapper.findPhotosByMemberId(memberId).stream()
                .collect(Collectors.groupingBy(CampReviewPhoto::getReviewId));
        return reviewMapper.findByMemberId(memberId).stream()
                .map(r -> CampReviewResponse.of(r, photos.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    @Transactional  // 스프링: 안의 SQL 을 한 묶음으로 — 중간에 실패하면 전부 취소
    public CampReviewResponse save(String loginId, Long planId, String content,
                                   List<Long> keepPhotoIds, List<MultipartFile> files) {
        return save(loginId, planId, content, keepPhotoIds, files, LocalDate.now(KST));
    }

    // 작성 · 수정을 한 곳에서: 없으면 만들고, 있으면 글을 바꾸고 사진은 "남길 것 + 새 것"으로 맞춘다
    // 사진 순서 = 남길 사진(keepPhotoIds 순서) 다음에 새 사진
    // today 를 받는 쪽은 테스트용 (날짜를 고정해서 확인)
    CampReviewResponse save(String loginId, Long planId, String content,
                            List<Long> keepPhotoIds, List<MultipartFile> files, LocalDate today) {
        Long memberId = findMember(loginId).getId();
        CampPlan plan = campPlanMapper.findById(memberId, planId);  // member_id 조건 → 남의 일정은 null
        if (plan == null) {
            throw new NotFoundException("등록한 캠핑을 찾을 수 없습니다.");
        }
        if (!plan.getCheckOut().isBefore(today)) {
            throw new InvalidRequestException("캠핑이 끝난 뒤에 후기를 쓸 수 있습니다.");
        }

        String text = content == null || content.isBlank() ? null : content.strip();
        if (text != null && text.length() > MAX_LEN) {
            throw new InvalidRequestException("후기는 " + MAX_LEN + "자까지 쓸 수 있습니다.");
        }
        List<Long> keep = keepPhotoIds == null ? List.of() : keepPhotoIds;
        List<MultipartFile> newFiles = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();

        CampReview review = reviewMapper.findByPlanId(memberId, planId);
        List<CampReviewPhoto> current = review == null ? List.of() : reviewMapper.findPhotos(review.getId());

        // 남길 사진은 이 후기의 사진이어야 한다 (다른 후기 사진 id 를 섞어 보내지 못하게)
        Set<Long> currentIds = current.stream().map(CampReviewPhoto::getId).collect(Collectors.toSet());
        if (!currentIds.containsAll(keep) || new HashSet<>(keep).size() != keep.size()) {
            throw new InvalidRequestException("사진 정보가 맞지 않습니다. 새로고침 후 다시 시도해 주세요.");
        }
        if (keep.size() + newFiles.size() > MAX_PHOTOS) {
            throw new InvalidRequestException("사진은 " + MAX_PHOTOS + "장까지 올릴 수 있습니다.");
        }
        if (text == null && keep.isEmpty() && newFiles.isEmpty()) {
            throw new InvalidRequestException("후기 글이나 사진을 넣어 주세요.");
        }
        // 파일 검사는 DB · 디스크를 건드리기 전에 전부 끝낸다 (한 장이라도 틀리면 아무것도 저장하지 않음)
        List<String> exts = new ArrayList<>();
        for (MultipartFile f : newFiles) {
            if (f.getSize() > MAX_BYTES) {
                throw new InvalidRequestException("사진은 한 장에 10MB까지 올릴 수 있습니다.");
            }
            exts.add(imageExt(f));
        }

        List<String> savedNames = new ArrayList<>();  // 이번에 새로 쓴 파일 (실패하면 지운다)
        try {
            if (review == null) {
                review = new CampReview();
                review.setPlanId(planId);
                review.setMemberId(memberId);
                review.setContent(text);
                reviewMapper.insert(review);
            } else {
                reviewMapper.updateContent(review.getId(), text);
            }

            for (CampReviewPhoto p : current) {
                if (!keep.contains(p.getId())) {
                    reviewMapper.deletePhoto(p.getId());
                }
            }
            for (int i = 0; i < keep.size(); i++) {
                reviewMapper.updatePhotoOrder(keep.get(i), i);
            }
            for (int i = 0; i < newFiles.size(); i++) {
                String name = UUID.randomUUID() + "." + exts.get(i);  // 원래 파일 이름은 쓰지 않는다
                try (InputStream in = newFiles.get(i).getInputStream()) {
                    store.save(name, in);
                }
                savedNames.add(name);
                CampReviewPhoto photo = new CampReviewPhoto();
                photo.setReviewId(review.getId());
                photo.setFileName(name);
                photo.setSortOrder(keep.size() + i);
                reviewMapper.insertPhoto(photo);
            }
        } catch (IOException e) {
            savedNames.forEach(store::delete);
            throw new UncheckedIOException("후기 사진 저장 실패", e);  // → 500 (내용은 로그에만)
        } catch (RuntimeException e) {
            savedNames.forEach(store::delete);
            throw e;
        }

        // 뺀 사진 파일은 DB 저장이 확정된 뒤에 지운다 (먼저 지웠는데 DB 가 취소되면 사진이 깨지므로)
        List<String> removed = current.stream().filter(p -> !keep.contains(p.getId()))
                .map(CampReviewPhoto::getFileName).toList();
        afterCommit(() -> removed.forEach(store::delete));

        CampReview saved = reviewMapper.findByPlanId(memberId, planId);  // created_at · updated_at 을 DB 값으로
        return CampReviewResponse.of(saved != null ? saved : review, reviewMapper.findPhotos(review.getId()));
    }

    // 사진 한 장 (내 일정의 후기 사진만)
    public PhotoFile loadPhoto(String loginId, Long planId, Long photoId) {
        Long memberId = findMember(loginId).getId();
        CampReviewPhoto photo = reviewMapper.findPhoto(memberId, planId, photoId);
        Resource resource = photo == null ? null : store.load(photo.getFileName());
        if (resource == null) {
            throw new NotFoundException("사진을 찾을 수 없습니다.");
        }
        return new PhotoFile(resource, mediaType(photo.getFileName()));
    }

    // 파일 앞부분(매직 넘버)으로 진짜 이미지인지 본다. 이름 · 브라우저가 알려 준 형식은 바꿔 보낼 수 있어서 믿지 않는다
    static String imageExt(MultipartFile f) {
        byte[] h = new byte[12];
        int n;
        try (InputStream in = f.getInputStream()) {
            n = in.readNBytes(h, 0, h.length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
        if (n >= 4 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
        if (n >= 4 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') return "gif";
        if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        throw new InvalidRequestException("사진은 jpg · png · webp · gif 만 올릴 수 있습니다.");
    }

    private static MediaType mediaType(String fileName) {
        if (fileName.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (fileName.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (fileName.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }

    // DB 묶음(@Transactional)이 성공으로 끝난 뒤에 실행. 묶음 밖(테스트)이면 바로 실행
    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private Member findMember(String loginId) {
        Member m = memberMapper.findByLoginId(loginId);
        if (m == null) {
            throw new NotFoundException("회원 정보를 찾을 수 없습니다.");
        }
        return m;
    }
}
