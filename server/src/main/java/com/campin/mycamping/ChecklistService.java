package com.campin.mycamping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.campin.common.NotFoundException;
import com.campin.member.Member;
import com.campin.member.MemberMapper;
import com.campin.weather.DayForecast;

import lombok.RequiredArgsConstructor;

// 출발 준비 체크리스트 (내 캠핑 카드 → 출발 준비). 항목 문구는 ChecklistItems, 가져오는 값은 ChecklistSource
//  - 계절 · 공통 준비물은 언제든 보인다
//  - 특보는 "지금 발효 중"인 것만 알 수 있어서 출발 3일 전부터(캠핑 중 포함) 확인한다 — 먼 일정은 특보를 부르지 않는다
//  - 날씨 예보(기상청 단기예보)도 같은 시기에만 가져온다. 4~7일 앞 중기예보는 아직 없다 (지역 코드 표를 공식 문서로 확인한 뒤 추가)
@Service
@RequiredArgsConstructor
public class ChecklistService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");  // "오늘"은 한국 날짜 기준
    static final int NEAR_DAYS = 3;  // 특보·예보를 보여 주기 시작하는 날 = 체크인 3일 전 (단기예보가 가까운 며칠만 나오는 점에 맞춤)
    static final int MAX_FORECAST_DAYS = 7;  // 예보 칸은 한 번에 최대 7일 (긴 캠핑이어도 화면이 길어지지 않게)

    private final MemberMapper memberMapper;
    private final CampPlanMapper campPlanMapper;
    private final ChecklistSource source;

    // 일정이 지금 어느 때인지: 지났는지 · 특보 확인 시기인지 · 특보가 보이기 시작하는 날
    record Window(boolean past, boolean near, LocalDate openDate) {
    }

    static Window windowOf(LocalDate today, LocalDate checkIn, LocalDate checkOut) {
        boolean past = checkOut.isBefore(today);
        LocalDate openDate = checkIn.minusDays(NEAR_DAYS);
        return new Window(past, !past && !today.isBefore(openDate), openDate);
    }

    public ChecklistResponse find(String loginId, Long planId) {
        return find(loginId, planId, LocalDate.now(KST));
    }

    // today 를 받는 쪽은 테스트용 (날짜를 고정해서 확인)
    ChecklistResponse find(String loginId, Long planId, LocalDate today) {
        Member member = memberMapper.findByLoginId(loginId);
        if (member == null) {
            throw new NotFoundException("회원 정보를 찾을 수 없습니다.");
        }
        // 내 일정이거나 같은 가족방 사람의 일정만 (가족 캠핑 카드에서도 출발 준비를 본다). 그 밖은 없는 것처럼 404
        CampPlan plan = campPlanMapper.findVisible(member.getId(), planId);
        if (plan == null) {
            throw new NotFoundException("등록한 캠핑을 찾을 수 없습니다.");
        }

        Window w = windowOf(today, plan.getCheckIn(), plan.getCheckOut());
        boolean spot = plan.getCampId() == null;
        Optional<ChecklistSource.Target> target = spot
                ? Optional.of(source.ofSpot(plan.getLat(), plan.getLng()))
                : source.ofCamp(plan.getCampId());

        // 특보: 확인 시기이고 위치를 알 때만. 못 받으면(warningsAvailable=false) 예시를 대신 쓰지 않고 비워 둔다
        List<ChecklistResponse.Warning> warnings = List.of();
        boolean available = false;
        if (w.near() && target.isPresent()) {
            Optional<List<ChecklistResponse.Warning>> got = source.warnings(target.get().lat(), target.get().lng());
            available = got.isPresent();
            warnings = got.orElse(List.of());
        }

        // 날씨 예보: 특보와 같은 시기에만. 못 받으면 available=false 로 두고 예시 값을 만들어 쓰지 않는다
        ChecklistResponse.Forecast forecast = null;
        if (w.near()) {
            forecast = target.isPresent()
                    ? forecastOf(source.forecast(target.get().lat(), target.get().lng()), today, plan)
                    : new ChecklistResponse.Forecast(false, List.of());
        }
        // 예보를 실제로 받았을 때만 날씨 항목을 만든다 (못 받았으면 null → 기존 항목만)
        List<DayForecast> forecastDays = forecast != null && forecast.available() ? forecast.days() : null;

        String season = ChecklistItems.seasonOf(plan.getCheckIn().getMonthValue());
        List<ChecklistItem> items = ChecklistItems.build(season, spot,
                warnings.stream().map(ChecklistResponse.Warning::category).toList(), forecastDays);

        return new ChecklistResponse(
                new ChecklistResponse.Plan(plan.getId(), plan.getCampId(), plan.getCampName(), plan.getLat(),
                        plan.getLng(), plan.getCheckIn(), plan.getCheckOut()),
                season, w.past(), w.near(), w.openDate(), available, warnings, forecast,
                target.map(ChecklistSource.Target::safety).orElse(null), items);
    }

    // 캠핑 기간 중 오늘 이후의 날(최대 MAX_FORECAST_DAYS 일)마다 한 칸. 기상청이 주지 않는 날은 "값 없음" 칸
    static ChecklistResponse.Forecast forecastOf(Optional<List<DayForecast>> got, LocalDate today, CampPlan plan) {
        if (got.isEmpty()) {
            return new ChecklistResponse.Forecast(false, List.of());
        }
        Map<LocalDate, DayForecast> byDate = got.get().stream()
                .collect(Collectors.toMap(DayForecast::date, d -> d, (a, b) -> a));
        List<DayForecast> days = new ArrayList<>();
        LocalDate first = plan.getCheckIn().isBefore(today) ? today : plan.getCheckIn();  // 캠핑 중이면 오늘부터
        for (LocalDate d = first; !d.isAfter(plan.getCheckOut()) && days.size() < MAX_FORECAST_DAYS; d = d.plusDays(1)) {
            days.add(byDate.getOrDefault(d, DayForecast.none(d)));
        }
        return new ChecklistResponse.Forecast(true, days);
    }
}
