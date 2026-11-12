package com.campin.saved;

import java.util.List;

// 하트(관심 캠핑장) 상태: '관심 캠핑장' 기본 목록 id 와 거기 담긴 캠핑장 id 들
// 화면은 이걸로 하트를 칠하고, 하트를 누르면 이 목록에 담고 뺀다
public record FavoritesResponse(Long listId, List<Long> campIds) {
}
