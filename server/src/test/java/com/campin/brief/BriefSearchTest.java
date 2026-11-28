package com.campin.brief;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

// 벡터 DB · OpenAI 없이 확인할 수 있는 것만 본다 (설정이 없을 때 꺼지는지, 벡터를 글자로 바꾸는 모양, 검색 조건)
class BriefSearchTest {

    @Test
    void 벡터DB_주소가_없으면_꺼지고_부르면_알려준다() {
        SafetyDocRepository repo = new SafetyDocRepository("", "", "");
        assertFalse(repo.isEnabled());
        assertThrows(IllegalStateException.class, repo::countChunks);
        repo.destroy();  // 꺼진 상태에서 닫아도 오류가 나지 않아야 한다
    }

    @Test
    void OpenAI_키가_없으면_부르지_않고_빈_결과() {
        EmbeddingClient client = new EmbeddingClient("  ");
        assertFalse(client.isEnabled());
        assertTrue(client.embed("계곡에 비가 많이 와요").isEmpty());
    }

    @Test
    void 벡터를_pgvector_글자_모양으로_바꾼다() {
        assertEquals("[0.5,-0.25,1.0E-5]", SafetyDocRepository.toVectorText(new float[] { 0.5f, -0.25f, 0.00001f }));
        assertEquals("[]", SafetyDocRepository.toVectorText(new float[0]));
    }

    @Test
    void 동행에_따라_아이_반려동물_조각을_남기거나_뺀다() {
        assertEquals(List.of("all"), SafetyDocFilter.of(false, false, null, null).companions());
        assertEquals(List.of("all", "child"), SafetyDocFilter.of(true, false, null, null).companions());
        assertEquals(List.of("all", "pet"), SafetyDocFilter.of(false, true, null, null).companions());
        assertEquals(List.of("all", "child", "pet"), SafetyDocFilter.of(true, true, null, null).companions());
    }

    @Test
    void 숙박을_모르면_거르지_않고_알면_그것만_남긴다() {
        assertEquals(List.of("all", "tent", "car"), SafetyDocFilter.of(false, false, null, null).sleepTypes());
        assertEquals(List.of("all", "tent"), SafetyDocFilter.of(false, false, "tent", null).sleepTypes());
        assertEquals(List.of("all", "car"), SafetyDocFilter.of(false, false, "car", null).sleepTypes());
        assertThrows(IllegalArgumentException.class, () -> SafetyDocFilter.of(false, false, "glamping", null));
    }

    @Test
    void 반대_계절만_뺀다() {
        assertEquals(List.of("summer"), SafetyDocFilter.excludedSeasons(LocalDate.of(2026, 12, 1)));
        assertEquals(List.of("summer"), SafetyDocFilter.excludedSeasons(LocalDate.of(2027, 2, 28)));
        assertEquals(List.of("winter"), SafetyDocFilter.excludedSeasons(LocalDate.of(2026, 6, 1)));
        assertEquals(List.of("winter"), SafetyDocFilter.excludedSeasons(LocalDate.of(2026, 8, 31)));
        // 봄 · 가을은 거르지 않는다 (11월 말에도 겨울 조각이 남아야 한다)
        assertEquals(List.of(), SafetyDocFilter.excludedSeasons(LocalDate.of(2026, 11, 28)));
        assertEquals(List.of(), SafetyDocFilter.excludedSeasons(LocalDate.of(2026, 3, 1)));
        assertEquals(List.of(), SafetyDocFilter.excludedSeasons(null));
    }

    @Test
    void 뺄_목록은_사용자가_정한_12개() {
        assertEquals(12, SafetyDocFilter.EXCLUDED_IDS.size());
        assertEquals(12, Set.copyOf(SafetyDocFilter.EXCLUDED_IDS).size());  // 같은 번호가 두 번 들어가지 않았는지
        assertTrue(SafetyDocFilter.EXCLUDED_IDS.contains("JD-004"));
        assertTrue(SafetyDocFilter.EXCLUDED_IDS.contains("JD-058"));
    }
}
