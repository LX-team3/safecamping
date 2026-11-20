package com.campin.mycamping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

// 후기 사진을 서버 폴더에 둔다: {CAMPIN_UPLOAD_DIR}/reviews/{UUID}.jpg
// 폴더는 환경변수 CAMPIN_UPLOAD_DIR 로 바꿀 수 있고, 없으면 서버를 켠 곳의 uploads/ (git 에는 올리지 않음 — .gitignore)
@Component  // 스프링: 이 클래스를 빈으로 등록 → CampReviewService 에 ReviewPhotoStore 로 주입된다
public class LocalReviewPhotoStore implements ReviewPhotoStore {

    private static final Logger log = LoggerFactory.getLogger(LocalReviewPhotoStore.class);

    // 서비스가 만든 이름 모양만 허용 → "../" 같은 이름으로 폴더 밖 파일을 건드리지 못하게
    private static final Pattern SAFE_NAME = Pattern.compile("^[0-9a-f-]{36}\\.(jpg|png|webp|gif)$");

    private final Path dir;

    // @Value: 설정값 · 환경변수를 읽는다. CAMPIN_UPLOAD_DIR 가 없으면 "uploads"
    public LocalReviewPhotoStore(@Value("${CAMPIN_UPLOAD_DIR:uploads}") String uploadDir) {
        this.dir = Paths.get(uploadDir, "reviews").toAbsolutePath().normalize();
    }

    @Override
    public void save(String fileName, InputStream in) throws IOException {
        Files.createDirectories(dir);
        Files.copy(in, path(fileName), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Resource load(String fileName) {
        Path p = path(fileName);
        return Files.isRegularFile(p) ? new FileSystemResource(p) : null;
    }

    @Override
    public void delete(String fileName) {
        try {
            Files.deleteIfExists(path(fileName));
        } catch (IOException | IllegalArgumentException e) {
            // 파일이 남을 뿐 화면 동작에는 영향이 없어서 로그만 남긴다
            log.warn("후기 사진 파일을 지우지 못함: {}", fileName, e);
        }
    }

    private Path path(String fileName) {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("잘못된 사진 파일 이름");
        }
        return dir.resolve(fileName);
    }
}
