package com.campin.brief;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// AI 브리핑 10단계: Claude 답을 한 줄씩 검사한다. 통과한 줄만 화면에 간다
// 왜 코드로 한 번 더 보나: 프롬프트 규칙(BriefPrompt.SYSTEM)은 대부분 지켜지지만 100% 는 아니다
//   → 안전에 중요한 규칙은 여기서 확실하게 막는다. 고치지 않고 "버린다" (고치다 뜻이 바뀌면 더 위험해서)
//
// 줄을 버리는 경우 (규칙 번호는 SYSTEM 의 제목)
//   [근거] sources 가 비었거나, 넘긴 자료 밖의 id 가 있거나, 3개 이상
//   [숫자] 답에 나온 숫자가 <situation> · 자료 제목 · 본문 어디에도 없음
//   [안전] "안전해요" · "괜찮아요" 같은 보증하는 말
//   [형식] 글이 비었거나 60자 넘음, 앞 줄과 같은 글, 4번째 줄부터
final class BriefCheck {

    static final int MAX_LINES = 3;
    static final int MAX_CHARS = 60;
    static final int MAX_SOURCES = 2;
    // 보증하는 말. "안전하게 대피해요"처럼 행동을 꾸미는 말은 막지 않으려고 끝맺는 꼴만 적는다
    static final List<String> GUARANTEE = List.of("안전해요", "안전합니다", "괜찮아요", "괜찮습니다", "안심하");

    private BriefCheck() {
    }

    record Result(List<BriefResult.Line> lines, List<String> dropped) {
    }

    // situation = BriefPrompt.situationOf(ctx) (Claude 에게 보낸 상황 글 그대로), docs = 넘긴 자료
    static Result check(BriefAnswer answer, String situation, List<SafetyDocRepository.Chunk> docs) {
        Map<String, SafetyDocRepository.Chunk> byId = new LinkedHashMap<>();
        docs.forEach(d -> byId.put(d.id(), d));

        // 쓸 수 있는 숫자 = 상황 글 + 자료 제목 · 본문의 숫자 (자료 id 의 숫자는 넣지 않는다: JD-031 의 31 등)
        Set<String> allowed = new HashSet<>(numbersIn(situation));
        docs.forEach(d -> {
            allowed.addAll(numbersIn(d.heading()));
            allowed.addAll(numbersIn(d.content()));
        });

        List<BriefResult.Line> kept = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (BriefAnswer.Line l : answer.lines() == null ? List.<BriefAnswer.Line>of() : answer.lines()) {
            String text = l == null || l.text() == null ? "" : l.text().strip();
            String why = reasonToDrop(text, l == null ? null : l.sources(), byId, allowed, seen, kept.size());
            if (why != null) {
                dropped.add(why + ": " + text);
                continue;
            }
            seen.add(text);
            kept.add(new BriefResult.Line(text, l.sources().stream().map(id -> sourceOf(byId.get(id))).toList()));
        }
        return new Result(kept, dropped);
    }

    // 버릴 이유 (통과하면 null)
    private static String reasonToDrop(String text, List<String> sources, Map<String, SafetyDocRepository.Chunk> byId,
                                       Set<String> allowed, Set<String> seen, int keptCount) {
        if (keptCount >= MAX_LINES) return "4번째 줄부터";
        if (text.isEmpty()) return "빈 글";
        if (text.codePointCount(0, text.length()) > MAX_CHARS) return MAX_CHARS + "자 넘음";
        if (seen.contains(text)) return "같은 글";
        if (sources == null || sources.isEmpty()) return "근거 없음";
        if (sources.size() > MAX_SOURCES) return "근거 " + MAX_SOURCES + "개 넘음";
        if (!byId.keySet().containsAll(sources)) return "넘기지 않은 근거 " + sources;
        for (String n : numbersIn(text)) {
            if (!allowed.contains(n)) return "상황 · 자료에 없는 숫자 " + n;
        }
        for (String g : GUARANTEE) {
            if (text.contains(g)) return "보증하는 말 '" + g + "'";
        }
        return null;
    }

    // 글 속 숫자들을 같은 모양으로: "1,600" → "1600", "05" → "5", "2.50" → "2.5" (-5 의 - 는 빼고 5 로 본다)
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    static Set<String> numbersIn(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        Matcher m = NUMBER.matcher(s);
        while (m.find()) {
            String raw = m.group().replace(",", "");
            out.add(new BigDecimal(raw).stripTrailingZeros().toPlainString());
        }
        return out;
    }

    private static BriefResult.Source sourceOf(SafetyDocRepository.Chunk d) {
        return new BriefResult.Source(d.id(), d.org(), d.docYear(), d.heading(), d.source(), d.page());
    }
}
