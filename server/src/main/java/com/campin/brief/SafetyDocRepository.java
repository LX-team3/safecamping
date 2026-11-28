package com.campin.brief;

import java.util.List;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

// AI 브리핑용 안전자료 조각(safety_doc_chunk, 120개)을 읽는 창구. 조각은 서비스 DB 가 아니라 벡터 DB(campine_vector)에 있다
//
// 왜 DB 접속을 이 클래스 안에서만 만드나:
//   DB 접속(DataSource)을 스프링에 하나 더 "등록"하면, 지금 MyBatis 가 쓰는 기본 접속 자동 설정이 꺼진다
//   → 그래서 등록하지 않고 여기서만 쓴다. 기존 서비스 DB 연결은 그대로다
//
// 접속 정보는 환경변수로만 받는다 (작업 PC: application-local.properties, 배포 서버: /etc/campine/campine.env)
//   VECTOR_DB_URL      예) 작업 PC jdbc:postgresql://localhost:15432/campine_vector (SSH 터널) / 서버 jdbc:postgresql://127.0.0.1:5432/campine_vector
//   VECTOR_DB_USERNAME
//   VECTOR_DB_PASSWORD
// 주소가 비어 있으면 벡터 기능만 꺼지고 서버는 그대로 켜진다 (벡터 DB 가 없는 팀원 PC · 테스트)
@Slf4j
@Component  // 스프링 빈으로 등록 → 다른 클래스가 생성자로 받아 쓴다
public class SafetyDocRepository implements DisposableBean {  // DisposableBean: 서버를 끌 때 destroy() 로 접속을 닫는다

    private final HikariDataSource dataSource;  // 꺼져 있으면 null
    private final JdbcTemplate jdbc;            // SQL 을 보내고 결과를 받는 도구 (꺼져 있으면 null)

    // @Value("${이름:}") = 환경변수 · 설정값을 읽고, 없으면 빈 문자열
    public SafetyDocRepository(@Value("${VECTOR_DB_URL:}") String url,
                               @Value("${VECTOR_DB_USERNAME:}") String username,
                               @Value("${VECTOR_DB_PASSWORD:}") String password) {
        if (url.isBlank()) {
            log.warn("VECTOR_DB_URL 환경변수가 없어 안전자료 검색(벡터 DB)을 끕니다.");
            this.dataSource = null;
            this.jdbc = null;
            return;
        }
        HikariDataSource ds = new HikariDataSource();  // 빈 생성자: 처음 쓸 때 접속한다 → 터널이 꺼져 있어도 서버는 켜진다
        ds.setJdbcUrl(url.strip());
        ds.setUsername(username.strip());
        ds.setPassword(password);  // 비밀번호는 로그에 남기지 않는다
        ds.setPoolName("vector-db");
        ds.setMaximumPoolSize(2);        // 브리핑 때만 잠깐 읽는다. 서버 DB 접속 수를 아끼려고 작게
        ds.setConnectionTimeout(5_000);  // 5초 안에 못 붙으면 포기 → 브리핑이 오래 멈추지 않게
        ds.setReadOnly(true);            // 읽기만 한다. 실수로 고치는 SQL 을 막는다
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);
    }

    // 벡터 DB 를 쓸 수 있게 설정됐는지 (접속이 되는지는 실제로 불러 봐야 안다)
    public boolean isEnabled() {
        return jdbc != null;
    }

    // 조각 수, 그중 임베딩이 있는 수 → 둘 다 120 이어야 한다
    public long[] countChunks() {
        requireEnabled();
        return jdbc.queryForObject(
                "SELECT count(*), count(embedding) FROM safety_doc_chunk",
                (rs, n) -> new long[] { rs.getLong(1), rs.getLong(2) });
    }

    // 조각 하나(id)와 뜻이 가까운 조각 limit 개. 접속 · 벡터 검색 확인용
    // <=> = 코사인 거리 (0 이면 같은 뜻, 작을수록 비슷)
    public List<Nearest> findNearestTo(String id, int limit) {
        requireEnabled();
        return jdbc.query("""
                SELECT id, heading,
                       embedding <=> (SELECT embedding FROM safety_doc_chunk WHERE id = ?) AS distance
                FROM safety_doc_chunk
                ORDER BY distance
                LIMIT ?
                """,
                (rs, n) -> new Nearest(rs.getString("id"), rs.getString("heading"), rs.getDouble("distance")),
                id, limit);
    }

    // 질문 벡터(EmbeddingClient 결과)와 뜻이 가까운 조각 limit 개. 거르지 않음 (확인용. 브리핑은 아래 search)
    // ?::vector = 글자 "[0.1,0.2,…]" 를 pgvector 벡터로 바꾼다 (pgvector 전용 자바 라이브러리 없이 쓰려고)
    public List<Nearest> findSimilar(float[] queryVector, int limit) {
        requireEnabled();
        return jdbc.query("""
                SELECT id, heading, embedding <=> ?::vector AS distance
                FROM safety_doc_chunk
                ORDER BY distance
                LIMIT ?
                """,
                (rs, n) -> new Nearest(rs.getString("id"), rs.getString("heading"), rs.getDouble("distance")),
                toVectorText(queryVector), limit);
    }

    // 조각 하나의 본문 (확인용: 같은 글을 다시 임베딩해 자기 자신이 1등으로 나오는지 본다)
    public String findContent(String id) {
        requireEnabled();
        return jdbc.queryForObject("SELECT content FROM safety_doc_chunk WHERE id = ?", String.class, id);
    }

    // AI 브리핑에 넘길 안전자료 (6단계): 꼬리표로 거르기 + 뺄 목록 제외 + 뜻이 가까운 순 상위 TOP_K 개
    // 조건은 SafetyDocFilter 한곳에서 정한다
    //   = ANY(?)  : 칸 값이 목록 중 하나면 남김 (동행 · 숙박)
    //   <> ALL(?) : 칸 값이 목록 어느 것과도 다르면 남김 (뺄 계절 · 뺄 조각). 목록이 비면 모두 남김
    //   coalesce  : 꼬리표가 비어 있으면 all 로 본다 (지금 120개는 모두 차 있음)
    //   is_reviewed : 사람이 검수한 조각만 (지금 120개 모두 true. 나중에 넣는 조각이 검수 전에 나가지 않게)
    // 목록은 String[] 로 넘긴다 → PostgreSQL 드라이버가 text[] 로 바꿔 준다
    public List<Chunk> search(float[] queryVector, SafetyDocFilter filter) {
        requireEnabled();
        return jdbc.query("""
                SELECT id, source, org, doc_year, page, heading, content, kind,
                       embedding <=> ?::vector AS distance
                FROM safety_doc_chunk
                WHERE is_reviewed
                  AND coalesce(companion, 'all')  = ANY(?::text[])
                  AND coalesce(sleep_type, 'all') = ANY(?::text[])
                  AND coalesce(season, 'all')    <> ALL(?::text[])
                  AND id <> ALL(?::text[])
                ORDER BY distance
                LIMIT ?
                """,
                (rs, n) -> new Chunk(
                        rs.getString("id"), rs.getString("source"), rs.getString("org"),
                        (Integer) rs.getObject("doc_year"),  // 연도가 없을 수 있어 getInt(빈 값이면 0) 대신 getObject
                        rs.getString("page"), rs.getString("heading"), rs.getString("content"),
                        rs.getString("kind"), rs.getDouble("distance")),
                toVectorText(queryVector),
                filter.companions().toArray(String[]::new),
                filter.sleepTypes().toArray(String[]::new),
                filter.excludedSeasons().toArray(String[]::new),
                SafetyDocFilter.EXCLUDED_IDS.toArray(String[]::new),
                TOP_K);
    }

    // AI 에게 넘길 조각 수. 3줄 브리핑에 근거로 쓰기에 충분하고, 프롬프트가 길어지지 않게
    static final int TOP_K = 5;

    // float[] → "[0.1,0.2,…]". Float.toString 은 지역 설정과 상관없이 소수점을 . 으로 쓴다
    static String toVectorText(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 12).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(v[i]));
        }
        return sb.append(']').toString();
    }

    private void requireEnabled() {
        if (jdbc == null) {
            throw new IllegalStateException("벡터 DB 가 꺼져 있습니다 (VECTOR_DB_URL 없음)");
        }
    }

    @Override
    public void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    // 검색 결과 한 줄: 조각 번호, 제목, 거리
    public record Nearest(String id, String heading, double distance) {}

    // 브리핑용 조각 한 개: 본문 + 출처(자료 이름 · 기관 · 연도 · 쪽) + 거리. 출처는 화면에 근거로 보여 준다
    public record Chunk(String id, String source, String org, Integer docYear, String page,
                        String heading, String content, String kind, double distance) {}
}
