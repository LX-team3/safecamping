package com.campin.push;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

// 캠핑 일정을 저장하면, 조금 뒤 저장한 회원과 같은 모임방 사람들의 폰으로 "캠핑 브리핑" 푸시를 보낸다
// 모임방이 없으면 저장한 회원에게만 간다. 보낸 뒤에 모임방에 들어온 사람에게는 따로 보내지 않는다
// 시연용이라 저장 10초 뒤에 보낸다 (실서비스라면 캠핑 전날 저녁). 예약은 서버 메모리에만 있어 서버를 끄면 사라진다
// 10초 안에 일정을 지우거나 고쳐도 알림은 그대로 간다 (시연용으로 정함)
@Service
public class BriefPushService {

    private static final Logger log = LoggerFactory.getLogger(BriefPushService.class);
    private static final long DELAY_MS = 10_000;

    private final PushTokenStore store;
    private final PushSender sender;
    private final RoommateMapper roommates;
    private final long delayMs;

    // 예약한 일을 정해진 시간 뒤에 따로 실행해 준다 → 저장 요청은 10초를 기다리지 않고 바로 응답한다
    // daemon: 서버를 끌 때 이 예약 때문에 종료가 막히지 않게
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "brief-push");
        thread.setDaemon(true);
        return thread;
    });

    // 생성자가 두 개라 스프링이 쓸 것을 @Autowired 로 알려 준다
    @Autowired
    public BriefPushService(PushTokenStore store, PushSender sender, RoommateMapper roommates) {
        this(store, sender, roommates, DELAY_MS);
    }

    // 테스트에서 10초를 기다리지 않게 시간을 바꿔 넣는다
    BriefPushService(PushTokenStore store, PushSender sender, RoommateMapper roommates, long delayMs) {
        this.store = store;
        this.sender = sender;
        this.roommates = roommates;
        this.delayMs = delayMs;
    }

    // 예약에 실패해도 예외를 던지지 않는다 → 이미 끝난 일정 저장이 실패한 것처럼 보이지 않게
    // toSelf: 저장한 본인 폰에도 보낼지 (앱에서 저장했으면 앱이 스스로 알림을 띄우므로 false)
    public void schedule(String loginId, Long planId, String campName, boolean toSelf) {
        try {
            timer.schedule(() -> send(loginId, planId, campName, toSelf), delayMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            log.warn("브리핑 푸시 예약 실패: {}", e.getClass().getSimpleName());
        }
    }

    // 받을 사람과 폰은 저장할 때가 아니라 보내는 지금 찾는다
    // → 그사이 모임방에 들어온 사람은 받고, 방을 나갔거나 로그아웃한 사람은 받지 않는다
    void send(String loginId, Long planId, String campName, boolean toSelf) {
        if (toSelf) {
            sendTo(loginId, planId, campName, "캠핑 브리핑이 도착했어요", "본인");
        }
        for (String roommate : findRoommates(loginId, planId)) {
            sendTo(roommate, planId, campName, "모임방 캠핑 브리핑이 도착했어요", "모임방");
        }
    }

    // 모임방을 못 찾아도(DB 오류 등) 예외를 던지지 않는다
    private List<String> findRoommates(String loginId, Long planId) {
        try {
            return roommates.findRoommates(loginId);
        } catch (RuntimeException e) {
            log.warn("브리핑 푸시: 모임방 사람을 찾지 못함: {} (일정 {})", e.getClass().getSimpleName(), planId);
            return List.of();
        }
    }

    // 한 사람에게 보낸다. 한 사람이 실패해도 다음 사람에게는 보내도록 여기서 예외를 막는다
    // 로그에는 회원 아이디 · 토큰을 남기지 않고 일정 id 와 본인 · 모임방 구분만 남긴다
    private void sendTo(String loginId, Long planId, String campName, String title, String who) {
        try {
            Optional<String> token = store.find(loginId);
            if (token.isEmpty()) {
                log.info("브리핑 푸시 안 보냄: 등록된 폰 없음 (일정 {}, {})", planId, who);
                return;
            }
            // 본인에게 가는 문구는 앱의 로컬 알림(client/src/utils/localNotify.js scheduleBriefNotice)과 같게
            // path · brief: 알림을 누르면 앱이 내 캠핑 화면을 열고 그 일정의 브리핑을 펼친다 (utils/pushNotify.js)
            boolean accepted = sender.send(token.get(),
                    title,
                    campName + " · 특보 · 날씨 · 준비물을 확인해 보세요",
                    Map.of("path", "/app/my-camping", "brief", String.valueOf(planId)));
            log.info("브리핑 푸시 {} (일정 {}, {})", accepted ? "보냄" : "보내지 못함", planId, who);
        } catch (RuntimeException e) {
            log.warn("브리핑 푸시 실패: {} (일정 {}, {})", e.getClass().getSimpleName(), planId, who);
        }
    }

    // @PreDestroy: 서버가 꺼질 때 스프링이 부른다
    @PreDestroy
    void stop() {
        timer.shutdownNow();
    }
}
