package com.campin.mycamping;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

// 테스트용 사진 저장소 (디스크 대신 메모리). failOnSave 를 켜면 저장 중 실패를 흉내 낸다
class FakeReviewPhotoStore implements ReviewPhotoStore {

    final Map<String, byte[]> files = new HashMap<>();
    boolean failOnSave = false;

    @Override public void save(String fileName, InputStream in) throws IOException {
        if (failOnSave) throw new IOException("디스크 가득 참 (테스트)");
        files.put(fileName, in.readAllBytes());
    }

    @Override public Resource load(String fileName) {
        byte[] b = files.get(fileName);
        return b == null ? null : new ByteArrayResource(b);
    }

    @Override public void delete(String fileName) {
        files.remove(fileName);
    }
}
