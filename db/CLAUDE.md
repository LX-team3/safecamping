# db — 데이터베이스 (미착수)

루트 `CLAUDE.md` 의 공통 규칙을 함께 따른다.
상태: `schema.sql` 아직 없음. 스택 PostgreSQL + PostGIS (확정)

## 기본환경
- DBMS: PostgreSQL + PostGIS
- 스키마 이름: [확정 필요] (현재 임시로 `campingsafe` 사용 중)
- 좌표계: WGS84 (EPSG:4326)(확인바람), 좌표 순서는 **경도(lon), 위도(lat)**
- 시간: `timestamptz`로 저장, 화면 표시 때만 한국 시간(KST)으로 변환

## 명명 규칙
- 테이블·컬럼: 소문자 + snake_case (예: `created_at`)
- 테이블명 단수/복수: [확정 필요] — 정한 뒤 **전부 같은 방식**으로 통일
- 한국어·대문자·공백·예약어를 이름에 쓰지 않는다.
- 불리언은 `is_`/`has_`로 시작 (예: `is_open`)
- 외래키 컬럼은 `참조테이블_id` 형태 (예: `camp_id`)

## 스키마 나누기 (2026-11-12, `schema_03.sql`)
- `raw` — 공공데이터 원본 (`camps` · `facilities` · `spot` · `score_weights` · `er_detail`). **읽기만 한다**
- `public` — 서버가 읽는 화면용 테이블 + 회원 데이터
- 화면용(`camp` · `facility` · `tour` · `camp_facility` · `camp_tour` · `place_info` · `place_note`)은 `merge_to_public.sql` 이 raw 에서 만든다
- 값이 틀리면: 원본·화면용을 직접 `UPDATE` 하지 말고 `merge_to_public.sql` 안에서 보정한다 (직접 고치면 다음 merge 때 사라짐)
- 회원 데이터(`member` · `saved_*` · `camp_plan` · `family_*` 등)만 직접 고친다
- 서버 SQL 에서 원본을 읽을 때는 `raw.camps` 처럼 스키마를 꼭 붙인다 (접속 기본 스키마가 public)

## 테이블 설계 기본

- 더미 데이터는 실제 데이터와 구분할 수 있게 넣는다 (안전 수치 규칙은 루트 CLAUDE.md)

## 스키마 변경 절차 (초안)
<!-- 승인·비밀 정보 규칙은 루트 CLAUDE.md 에 있으므로 여기 다시 적지 않는다 -->
1. 변경 계획(무엇을, 왜)을 보여주고 승인받는다
2. `schema.sql` 에 반영한다
3. 영향받는 백엔드 코드(`backend/`)를 함께 확인한다

