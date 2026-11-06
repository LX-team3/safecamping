package com.campin.saved;

// 저장 화면 목록 카드 하나. 카드의 회색 칸 개수는 campCount 로 그린다
public record SavedListResponse(Long id, String name, boolean isDefault, int campCount) {

    public static SavedListResponse from(SavedList list) {
        return new SavedListResponse(list.getId(), list.getName(),
                Boolean.TRUE.equals(list.getIsDefault()),
                list.getCampCount() == null ? 0 : list.getCampCount());
    }
}
