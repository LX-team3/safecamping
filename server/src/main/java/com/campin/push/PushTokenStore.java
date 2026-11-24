package com.campin.push;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

// "이 회원의 폰은 여기" 주소록 (로그인 아이디 → 폰 토큰)
// 시연용이라 서버 메모리에 둔다 → 서버를 끄면 비워지고, 앱을 다시 켜거나 로그인하면 다시 채워진다
// 실서비스에서는 이 클래스 안만 DB(mapper)로 바꾸면 된다
// 회원 한 명당 폰 한 대: 다른 폰에서 로그인하면 나중 폰으로 바뀐다
// synchronized: 여러 요청이 동시에 와도 한 번에 하나씩 고치게 (주소록이 섞이지 않게)
@Component
public class PushTokenStore {

    private final Map<String, String> tokens = new HashMap<>();

    public synchronized void register(String loginId, String token) {
        // 같은 폰을 다른 회원이 쓰고 있었다면 그 연결을 지운다 → 앞 사람의 알림이 이 폰으로 오지 않게
        tokens.values().removeIf(token::equals);
        tokens.put(loginId, token);
    }

    // 등록된 토큰이 "이 폰"일 때만 지운다 → 예전 폰에서 로그아웃해도 지금 폰의 연결은 남는다
    public synchronized void remove(String loginId, String token) {
        tokens.remove(loginId, token);
    }

    public synchronized Optional<String> find(String loginId) {
        return Optional.ofNullable(tokens.get(loginId));
    }
}
