package com.campin.saved;

import java.util.Arrays;
import java.util.List;

// 저장 화면 목록 카드 하나. 카드 썸네일 3칸: 사진(thumbUrls)부터 채우고, 사진 없는 캠핑장은 회색 칸 (campCount 로 개수)
public record SavedListResponse(Long id, String name, boolean isDefault, int campCount, List<String> thumbUrls) {

    public static SavedListResponse from(SavedList list) {
        String urls = list.getThumbUrls();
        List<String> thumbs = urls == null || urls.isBlank() ? List.of() : Arrays.asList(urls.trim().split(" +"));
        return new SavedListResponse(list.getId(), list.getName(),
                Boolean.TRUE.equals(list.getIsDefault()),
                list.getCampCount() == null ? 0 : list.getCampCount(), thumbs);
    }
}
