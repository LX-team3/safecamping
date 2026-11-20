package com.campin.mycamping;

import java.time.Duration;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.campin.common.ApiResponse;

import lombok.RequiredArgsConstructor;

// 지난 캠핑 후기 API. /api/member/** 는 SecurityConfig 에서 로그인 필요 → authentication 은 null 이 아니다
@RestController
@RequestMapping("/api/member/camp-plans")
@RequiredArgsConstructor
public class CampReviewController {

    private final CampReviewService service;

    // 내 후기 전부: GET /api/member/camp-plans/reviews
    @GetMapping("/reviews")
    public ApiResponse<List<CampReviewResponse>> list(Authentication authentication) {
        return ApiResponse.ok(service.findAll(authentication.getName()));
    }

    // 작성 · 수정 (덮어씀): POST /api/member/camp-plans/3/review   (multipart/form-data)
    //   content       글 (선택)
    //   keepPhotoIds  남길 기존 사진 id, 화면 순서대로 (선택, 여러 번)
    //   photos        새 사진 파일 (선택, 여러 번)
    // PUT 이 아니라 POST 인 이유: 서블릿 서버에 따라 PUT 의 multipart 를 읽지 못하는 경우가 있어서
    @PostMapping(value = "/{planId}/review", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CampReviewResponse> save(Authentication authentication, @PathVariable Long planId,
                                                @RequestParam(required = false) String content,
                                                @RequestParam(required = false) List<Long> keepPhotoIds,
                                                @RequestPart(required = false) List<MultipartFile> photos) {
        return ApiResponse.ok(service.save(authentication.getName(), planId, content, keepPhotoIds, photos));
    }

    // 사진 파일: GET /api/member/camp-plans/3/review/photos/7
    // 그림 파일 자체를 돌려주므로 ApiResponse 로 감싸지 않는다 (없거나 남의 사진이면 다른 API 와 같은 404 JSON)
    @GetMapping("/{planId}/review/photos/{photoId}")
    public ResponseEntity<Resource> photo(Authentication authentication, @PathVariable Long planId,
                                          @PathVariable Long photoId) {
        CampReviewService.PhotoFile file = service.loadPhoto(authentication.getName(), planId, photoId);
        return ResponseEntity.ok()
                .contentType(file.type())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())  // 같은 id 사진은 바뀌지 않아 브라우저에 둔다. 본인 사진이라 공용 캐시는 X
                .body(file.resource());
    }
}
