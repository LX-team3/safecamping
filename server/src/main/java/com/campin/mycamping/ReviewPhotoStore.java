package com.campin.mycamping;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;

// 후기 사진 파일을 두는 곳. 실제 구현은 LocalReviewPhotoStore (서버 폴더)
// 인터페이스로 뺀 이유: 테스트에서 디스크 대신 메모리에 두려고, 나중에 외부 저장소로 바꿀 때 이 구현만 갈아 끼우려고
public interface ReviewPhotoStore {

    // fileName 은 서비스가 만든 이름 (UUID + 확장자)
    void save(String fileName, InputStream in) throws IOException;

    // 없으면 null
    Resource load(String fileName);

    // 없어도 오류 내지 않는다 (지우는 게 목적이므로)
    void delete(String fileName);
}
