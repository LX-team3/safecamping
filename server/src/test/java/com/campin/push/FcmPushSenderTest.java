package com.campin.push;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.firebase.FirebaseApp;

// 키가 없거나 잘못돼도 서버가 멈추지 않는지, 키 모양이 맞으면 준비되는지 확인 (Firebase 로 실제 발송은 하지 않는다)
class FcmPushSenderTest {

    @TempDir
    Path dir;

    // Firebase 는 한 번 초기화하면 메모리에 남으므로 테스트마다 비운다
    @BeforeEach
    @AfterEach
    void clearFirebase() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    @Test
    void 키_경로가_없으면_보내지_않는다() {
        FcmPushSender sender = new FcmPushSender("");
        assertFalse(sender.connected());
        assertFalse(sender.send("token", "제목", "내용", Map.of()));
    }

    @Test
    void 키_파일이_없으면_보내지_않는다() {
        FcmPushSender sender = new FcmPushSender(dir.resolve("none.json").toString());
        assertFalse(sender.connected());
        assertFalse(sender.send("token", "제목", "내용", Map.of()));
    }

    @Test
    void 키_파일이_잘못되면_보내지_않는다() throws Exception {
        Path broken = Files.writeString(dir.resolve("broken.json"), "키가 아닌 글");
        assertFalse(new FcmPushSender(broken.toString()).connected());
    }

    @Test
    void 키_모양이_맞으면_준비되고_다시_켜도_오류가_없다() throws Exception {
        String path = fakeKey().toString();
        assertTrue(new FcmPushSender(path).connected());
        assertTrue(new FcmPushSender(path).connected());  // devtools 가 서버를 다시 켠 경우
    }

    @Test
    void 토큰이_비어_있으면_예외_없이_실패한다() throws Exception {
        FcmPushSender sender = new FcmPushSender(fakeKey().toString());
        assertFalse(sender.send("", "제목", "내용", Map.of("path", "/app/my-camping")));
    }

    // 진짜 키를 쓰지 않도록, 테스트할 때마다 모양만 같은 가짜 키를 만든다
    private Path fakeKey() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        String base64 = Base64.getEncoder().encodeToString(rsa.generateKeyPair().getPrivate().getEncoded());
        String json = """
                {
                  "type": "service_account",
                  "project_id": "fake-project",
                  "private_key_id": "fake",
                  "private_key": "-----BEGIN PRIVATE KEY-----\\n%s\\n-----END PRIVATE KEY-----\\n",
                  "client_email": "fake@fake-project.iam.gserviceaccount.com",
                  "client_id": "0"
                }
                """.formatted(base64);
        return Files.writeString(dir.resolve("fake.json"), json);
    }
}
