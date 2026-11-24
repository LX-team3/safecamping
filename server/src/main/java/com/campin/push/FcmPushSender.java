package com.campin.push;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

// Firebase(FCM)로 폰에 푸시 알림을 보낸다
// 키: 환경변수 GOOGLE_APPLICATION_CREDENTIALS 에 서비스 계정 키(JSON) 파일의 "경로"를 넣는다
//     키 파일은 비밀 정보라 프로젝트 폴더 밖에 두고, 코드·application.properties·로그에 내용을 쓰지 않는다
// @Component: 스프링이 이 클래스를 하나 만들어 두고 필요한 곳(PushSender 를 받는 곳)에 넣어 준다
@Component
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String CHANNEL_ID = "push-alert";  // 앱의 알림 채널 (client/src/utils/pushNotify.js 의 PUSH_CHANNEL_ID 와 같아야 함)
    private static final int CONNECT_TIMEOUT_MS = 3_000;  // Firebase 가 느려도 서버가 오래 기다리지 않게
    private static final int READ_TIMEOUT_MS = 5_000;

    private final FirebaseMessaging messaging;  // 키가 없거나 잘못됐으면 null → 알림만 안 보내고 서버는 그대로 동작

    // ${GOOGLE_APPLICATION_CREDENTIALS:} = 환경변수를 읽고, 없으면 빈 문자열
    public FcmPushSender(@Value("${GOOGLE_APPLICATION_CREDENTIALS:}") String keyPath) {
        this.messaging = connect(keyPath.strip());
    }

    private static FirebaseMessaging connect(String keyPath) {
        if (keyPath.isEmpty()) {
            log.warn("GOOGLE_APPLICATION_CREDENTIALS 환경변수가 없어 푸시 알림을 보내지 않습니다.");
            return null;
        }
        try (InputStream key = Files.newInputStream(Path.of(keyPath))) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(ServiceAccountCredentials.fromStream(key))
                    .setConnectTimeout(CONNECT_TIMEOUT_MS)
                    .setReadTimeout(READ_TIMEOUT_MS)
                    .build();
            // devtools 가 서버를 다시 켜도 Firebase 는 메모리에 남아 있다. 두 번 초기화하면 오류가 나므로 있으면 그것을 쓴다
            // (그래서 키 파일을 바꿨을 때는 서버를 완전히 껐다 켜야 한다)
            FirebaseApp app = FirebaseApp.getApps().isEmpty() ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance();
            FirebaseMessaging ready = FirebaseMessaging.getInstance(app);
            log.info("Firebase 연결 준비됨 (키 파일을 읽었습니다)");
            return ready;
        } catch (IOException | RuntimeException e) {
            // 오류 글에 키 내용이 섞일 수 있어 오류 종류만 남긴다
            log.warn("Firebase 키 파일을 쓸 수 없어 푸시 알림을 보내지 않습니다. 경로와 파일을 확인하세요: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    // 키를 읽어 보낼 준비가 됐는지 (테스트에서 확인)
    boolean connected() {
        return messaging != null;
    }

    @Override
    public boolean send(String token, String title, String body, Map<String, String> data) {
        if (messaging == null) {
            return false;
        }
        try {
            messaging.send(message(token, title, body, data));
            return true;
        } catch (FirebaseMessagingException e) {
            // 토큰은 폰의 주소라 로그에 남기지 않는다. UNREGISTERED = 앱을 지웠거나 토큰이 바뀜
            log.warn("푸시 발송 실패: {}", e.getMessagingErrorCode() != null ? e.getMessagingErrorCode() : e.getErrorCode());
            return false;
        } catch (RuntimeException e) {
            log.warn("푸시 발송 실패: {}", e.getClass().getSimpleName());  // 예) 토큰이 비어 있음
            return false;
        }
    }

    private static Message message(String token, String title, String body, Map<String, String> data) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)  // 화면이 꺼진 절전 상태에서도 바로 전달되게
                        .setNotification(AndroidNotification.builder().setChannelId(CHANNEL_ID).build())
                        .build())
                .build();
    }
}
