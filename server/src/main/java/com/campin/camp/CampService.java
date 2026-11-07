package com.campin.camp;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.campin.common.DriveTone;
import com.campin.common.InvalidRequestException;
import com.campin.place.PlaceMapper;

import lombok.RequiredArgsConstructor;

// 탐색 탭: 캠핑장 목록 · 내 유형 추천 / 캠핑장 상세
@Service
@RequiredArgsConstructor
public class CampService {

    // 안전 등급 기준 (server/CLAUDE.md). 점수 계산 방식이 정해지면 같이 다시 본다
    static final int SAFE_MIN = 70;    // 70 이상: 안전
    static final int CAUTION_MIN = 40; // 40~69: 주의, 40 미만: 위험

    private static final Set<String> CAMP_TYPES = Set.of("auto", "glamping", "backpacking");
    private static final Set<String> SORTS = Set.of("score", "er");
    private static final int MAX_LIMIT = 20;

    private final CampMapper campMapper;
    private final PlaceMapper placeMapper;

    public static String gradeOf(Integer score) {
        if (score == null) return "unknown";
        if (score >= SAFE_MIN) return "safe";
        if (score >= CAUTION_MIN) return "caution";
        return "danger";
    }

    // 전체 목록 (지도 마커 · "내 유형 맞춤" 끈 목록). 위험 등급도 포함
    public List<CampResponse> list(String sido, String sigungu, String campType, String sort) {
        return find(sido, sigungu, campType, sort).stream().map(CampResponse::from).toList();
    }

    // 내 유형 추천 — 임시 기준: 위험 등급 빼기 → 지역·캠핑 유형이 맞는 곳 → 정렬 → 위에서 limit 개
    // (진단 답을 점수에 반영하는 판정 로직은 나중에. 루트 CLAUDE.md "구현 방침")
    public CampRecommendResponse recommend(String sido, String sigungu, String campType, String sort, Integer limit) {
        int n = limit == null ? 5 : limit;
        if (n < 1 || n > MAX_LIMIT) {
            throw new InvalidRequestException("limit 은 1~" + MAX_LIMIT + " 사이여야 합니다.");
        }
        List<CampResponse> all = list(sido, sigungu, campType, sort);
        List<CampResponse> ok = all.stream().filter(c -> !c.grade().equals("danger")).limit(n).toList();
        List<CampResponse> excluded = all.stream().filter(c -> c.grade().equals("danger")).toList();
       return new CampRecommendResponse(ok, excluded);
    }

    // 캠핑장 상세: 기본 정보 + 가까운 시설(안전점수 탭·경로 지도) + 정보 줄(캠핑장 정보 탭)
    public CampDetailResponse detail(Long id) {
        Camp c = findCamp(id);
        List<CampDetailResponse.Facility> facilities = campMapper.findFacilities(id).stream()
                .map(f -> new CampDetailResponse.Facility(f.getId(), f.getType(), f.getName(), f.getLat(), f.getLng(),
                        f.getDistanceKm(), f.getDriveMin(), DriveTone.of(f.getDriveMin())))
                .toList();
        String grade = gradeOf(c.getSafetyScore());
        return new CampDetailResponse(c.getId(), c.getName(), c.getSido(), c.getSigungu(), c.getEupmyeon(),
                c.getCampType(), c.getLat(), c.getLng(), c.getSafetyScore(), grade, headlineOf(grade),
                c.getImageUrl(), c.getReservationUrl(), c.isDummy(), facilities,
                placeMapper.findInfos(PlaceMapper.CAMP, id));
    }

    // 관광지 탭: 내 취향(prefer: 여행 취향 선택지 code)과 맞는 곳이 앞, 그다음 인기순
    public List<CampTourResponse> tours(Long id, List<String> prefer) {
        findCamp(id);
        Set<String> wanted = prefer == null ? Set.of() : Set.copyOf(prefer);
        Comparator<CampTourRow> byRank = Comparator.comparing(CampTourRow::getPopularityRank,
                Comparator.nullsLast(Comparator.naturalOrder()));
        return campMapper.findTours(id).stream()
                .sorted(Comparator.comparing((CampTourRow t) -> !isWanted(t, wanted)).thenComparing(byRank))
                .map(t -> new CampTourResponse(t.getId(), t.getName(), t.getDriveMin(), t.getNote(),
                        isWanted(t, wanted)))
                .toList();
    }

    private static boolean isWanted(CampTourRow t, Set<String> wanted) {
        return wanted.contains(t.getCategory()) || (t.getNature() != null && wanted.contains(t.getNature()));
    }

    private Camp findCamp(Long id) {
        Camp camp = campMapper.findById(id);
        if (camp == null) {
            throw new InvalidRequestException("없는 캠핑장입니다.");
        }
        return camp;
    }

    // 점수 옆 한 줄. 안전은 스크린샷 문구, 나머지는 임시 문구
    private static String headlineOf(String grade) {
        return switch (grade) {
            case "safe" -> "밤에도 비교적 안심할 수 있어요";
            case "caution" -> "밤에는 주변 시설을 먼저 확인하세요";
            case "danger" -> "안전 정보를 꼭 확인하고 결정하세요";
            default -> "안전점수를 준비하고 있어요";
        };
    }

    private List<Camp> find(String sido, String sigungu, String campType, String sort) {
        String type = blankToNull(campType);
        if ("unknown".equals(type)) {
            type = null; // 진단에서 "모르겠어요" → 유형으로 거르지 않음
        }
        if (type != null && !CAMP_TYPES.contains(type)) {
            throw new InvalidRequestException("없는 캠핑 유형입니다: " + campType);
        }
        List<Camp> camps = campMapper.findCamps(blankToNull(sido), blankToNull(sigungu), type);
        return camps.stream().sorted(comparatorOf(sortOf(sort))).toList();
    }

    private String sortOf(String sort) {
        String s = blankToNull(sort);
        if (s == null) return "score";
        if (!SORTS.contains(s)) {
            throw new InvalidRequestException("정렬은 score 또는 er 만 됩니다.");
        }
        return s;
    }

    // score: 안전점수 높은 순 (같으면 응급실 가까운 순) / er: 응급실 가까운 순 (같으면 점수 높은 순)
    // 값이 없는(null) 캠핑장은 맨 뒤로
    private Comparator<Camp> comparatorOf(String sort) {
        Comparator<Camp> byScore = Comparator.comparing(Camp::getSafetyScore,
                Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<Camp> byEr = Comparator.comparing(Camp::getErDriveMin,
                Comparator.nullsLast(Comparator.naturalOrder()));
        return sort.equals("er") ? byEr.thenComparing(byScore) : byScore.thenComparing(byEr);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
