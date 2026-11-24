package com.campin.push;

import java.util.Map;

// 폰으로 푸시 알림을 보내는 곳
// 실제로는 FcmPushSender(Firebase). 인터페이스로 둔 이유: 테스트에서 진짜 알림을 보내지 않고 가짜를 넣기 위해
public interface PushSender {

    // token: 받을 폰의 주소(앱이 Firebase 에서 받은 기기 토큰)
    // data: 알림을 눌렀을 때 앱이 쓰는 값. 예) path=/app/my-camping, brief=일정id (앱의 utils/pushNotify.js 가 읽음)
    // Firebase 가 발송 요청을 받아 주면 true, 키가 없거나 실패하면 false (예외를 던지지 않는다 → 일정 저장 같은 원래 일은 그대로 끝난다)
    // true 라도 폰 화면에 떴다는 뜻은 아니다 (폰이 꺼져 있거나 알림을 꺼 두었을 수 있음)
    boolean send(String token, String title, String body, Map<String, String> data);
}
