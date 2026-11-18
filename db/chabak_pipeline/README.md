# 차박 판정 DB 자동 구축 파이프라인

손으로 하던 데이터 정리(압축 풀기 → 검사 → 적재 → SQL 정리 → 확인 → 덤프)를 명령 한 줄로 돌린다.

```
원본 zip/shp ──▶ ① extract ──▶ ② validate ──▶ ③ load ──▶ ④ transform ──▶ ⑤ report ──▶ ⑥ export
                 zip 풀기       좌표계·인코딩   raw 적재    02→03→05 SQL    품질 리포트     chabak dump
                                건수·PNU 검사   건수 대조   chabak 스키마    지난번과 비교   (공용 DB 배포용)
```

## 준비

```bat
pip install -r requirements.txt
```

- Python 3.10 이상, PostgreSQL + PostGIS (shp2pgsql, psql, pg_dump 사용)
- 내 컴퓨터에 `chabak` DB 를 만들고 `CREATE EXTENSION postgis;` (PostGIS 는 기본 위치 public 에)
- DB 접속은 환경변수로만 받는다 (코드·파일에 비밀번호를 쓰지 않음)

아래 명령은 **PowerShell** 기준이다 (VS Code 기본 터미널). 이 폴더(`db/chabak_pipeline`)에서 실행한다.
환경변수는 그 창에서만 쓰이고 창을 닫으면 사라진다 → 새 창을 열면 다시 넣는다.

```powershell
chcp 65001                         # 한글 깨짐 방지
$env:PGHOST = "localhost"          # localhost 가 아니면 load · transform 이 멈춤 (공용 DB 보호)
$env:PGUSER = "postgres"
$env:PGDATABASE = "chabak"
$env:PGPASSWORD = "로컬 postgres 비밀번호"
# PostgreSQL 설치 폴더가 기본(C:\Program Files\PostgreSQL\18\bin)과 다르면:
# $env:PGBIN = "C:\Program Files\PostgreSQL\17\bin"
```

## 실행

```powershell
# 검사만 (DB 안 건드림) — 새 데이터를 받았을 때 먼저 돌려 본다
python pipeline.py --data "C:\chabak_data\지적도데이터" --steps extract,validate

# 전부 (내 컴퓨터 chabak DB 의 raw · chabak 스키마를 지우고 새로 만든다)
python pipeline.py --data "C:\chabak_data\지적도데이터"

# 리포트 · dump 만 (적재·정리는 끝났을 때)
python pipeline.py --data "C:\chabak_data\지적도데이터" --steps report,export
```

cmd(명령 프롬프트)에서 할 때: 주석은 `::`, 환경변수는 `set PGHOST=localhost` 처럼 (따옴표 없이). `python …` 줄은 똑같다.

## 단계별로 자동 검사하는 것

| 단계 | 검사 | 왜 (실제로 겪은 문제) |
| --- | --- | --- |
| validate | `.prj` 투영 값으로 좌표계 판별 (5186 아니면 중단) | 같은 데이터의 `_5174_` 옛 좌표계 버전이 목록에 섞여 있었음 |
| validate | 실제 글자로 인코딩 판별 (`.cpg` 를 믿지 않음) | 토지특성정보 `.cpg` 는 UTF-8 인데 실제로는 EUC-KR |
| validate | PNU 가 51810 으로 시작하는 비율 99% 이상 | 강원특별자치도 출범으로 시도코드 42 → 51 |
| validate | `.shp/.shx/.dbf/.prj` 세트, 최소 건수 | LX맵 zip 안에 폴더가 한 단계 더 있어 파일을 못 찾았음 |
| load | 원본 건수 = DB 건수 | 적재 중 끊김 확인 |
| load | 테이블을 `DROP TABLE IF EXISTS` 로 먼저 지우고 `shp2pgsql -c` 로 새로 만듦 | `shp2pgsql -d` 는 처음 적재하는 DB 에서 "column not found in geometry_columns table" 로 멈춤 |
| load · transform | localhost 에서만 실행 (PGHOST 가 다른 주소면 멈춤) | 공용 DB 를 실수로 덮어쓰지 않게 (DB 를 바꾸는 단계는 전부) |
| report | LX맵 매칭 누락 1% 초과, 지난번보다 5% 넘게 변한 숫자 경고 | 기준일이 다른 데이터를 붙이면 856필지가 비었음 |

## 실행 결과 (2026-11-16, 인제군)

처음부터 끝까지 한 번에 돌려서 손으로 만든 결과와 같은 숫자가 나오는 것을 확인했다. 리포트: `reports/report_20261116_1640.md`

| 항목 | 결과 |
| --- | --- |
| 필지 (cadastre · land_attr) | 124,276 |
| 규제구역 | 845 (불가 621 · 주의 224) |
| LX맵 · 토지특성정보에 없는 필지 | 4 · 856 (기준일 차이) |
| 차박 후보지 (open_spot, 공공데이터상 제한 없음) | 46 |
| 판정 비율 (인제군 땅 위 무작위 311지점) | 주의 65.6% · 불가 34.4% |

첫 실행에서 찾아 고친 문제 (코드에 날짜와 함께 주석):
- `shp2pgsql -d` 는 처음 적재하는 DB 에서 멈춤 → `DROP TABLE IF EXISTS` + `shp2pgsql -c`
- `ST_Extent` 가 좌표계 번호를 잃어 판정 비율 쿼리가 멈춤 → `ST_SetSRID(…, 5186)`
- 결과 묶음 별명 `t` 가 칸 이름과 겹쳐 건수가 비어 나옴 → 별명 `_row`

## 새 데이터 추가

`datasets.py` 의 `DATASETS` 에 한 줄 추가 → 정리 SQL(`sql/02_public_tables.sql`)에 그 테이블을 쓰는 부분 추가.

## 공용 DB 반영

> **참고용 절차다. 이 문서도 파이프라인도 공용 DB 에 자동으로 아무것도 하지 않는다.**
> 공용 DB 는 팀이 같이 쓰므로 **DB 주인과 상의한 뒤, 사람이 한 줄씩 직접 실행**한다.
> 실행 전에 팀에 알린다 (넣는 동안은 서버의 차박 API `/api/chabak/**` 가 동작하지 않는다).

파이프라인은 내 컴퓨터 DB 용이다 (`load` · `transform` 은 PGHOST 가 localhost 가 아니면 멈춘다).
공용 DB 에는 `export` 로 만든 dump 를 아래 순서로 넣는다. 접속 정보는 자리표시(`<공용DB주소>` 등)로 두었다 — 그대로 붙여 넣으면 접속되지 않는다.

왜 그냥 `pg_restore` 하지 않나: 내 컴퓨터 DB 는 PostGIS 가 `public` 스키마에, 공용 DB 는 `extensions` 스키마에 있다.
그래서 dump 를 SQL 글로 풀어 `public.geometry` → `extensions.geometry` 로 바꾸고, 함수의 search_path 에도 `extensions` 를 넣어서 복원한다.

PowerShell 에서:

```powershell
# 0. 준비 (한글 깨짐 방지 + 도구 경로)
chcp 65001
$bin = "C:\Program Files\PostgreSQL\18\bin"

# 1. dump 를 SQL 글로 풀고 PostGIS 위치 바꾸기 (내 컴퓨터에서만 하는 작업)
& "$bin\pg_restore.exe" -f chabak.sql chabak_YYYYMMDD.dump
$t = [IO.File]::ReadAllText("$PWD\chabak.sql")
$t = $t -replace 'public\.geometry', 'extensions.geometry'
$t = $t -replace "'chabak', 'public'", "'chabak', 'extensions', 'public'"
[IO.File]::WriteAllText("$PWD\chabak_ext.sql", $t)

# 2. 공용 DB 접속 설정 (자리표시를 실제 값으로. 비밀번호는 실행할 때 물어봄 — 파일에 적지 않는다)
$env:PGHOST = "<공용DB주소>"
$env:PGUSER = "<사용자>"
$env:PGDATABASE = "<DB이름>"

# 3. 기존 chabak 스키마 지우기 (팀에 미리 알리기)
& "$bin\psql.exe" -c "DROP SCHEMA IF EXISTS chabak CASCADE;"

# 4. 복원
& "$bin\psql.exe" -v ON_ERROR_STOP=1 -f chabak_ext.sql

# 5. 등록 야영장 다시 채우기 (public.camp → chabak.campsite, '야영 가능' 판정·근처 캠핑장 추천이 켜짐)
& "$bin\psql.exe" -v ON_ERROR_STOP=1 -f 06_shared_db_after_restore.sql

# 6. 서버 계정(teamuser)에 읽기 권한 다시 주기 — 꼭 해야 함
#    3번에서 스키마를 지우면 권한도 같이 사라진다 → 안 하면 서버 차박 API 가 500 오류
#    chabak 스키마 주인(복원한 계정)이 실행한다
& "$bin\psql.exe" -v ON_ERROR_STOP=1 -c "GRANT USAGE ON SCHEMA chabak TO teamuser; GRANT SELECT ON ALL TABLES IN SCHEMA chabak TO teamuser; GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA chabak TO teamuser;"

# 7. 확인
& "$bin\psql.exe" -c "SELECT count(*) FROM chabak.cadastre;"                                        # 124,276 근처
& "$bin\psql.exe" -c "SELECT chabak.fn_verdict_json(38.128712, 128.422383)->>'verdict';"            # 불가 (설악산 국립공원)
& "$bin\psql.exe" -c "SELECT has_function_privilege('teamuser', 'chabak.fn_verdict_json(double precision, double precision)', 'EXECUTE');"  # t
```

- `06_shared_db_after_restore.sql` 은 아직 이 폴더에 없다 (처음 만든 곳: 내려받은 `sql` 폴더). 이 폴더의 `sql/` 로 옮겨 두면 찾기 쉽다
- 서버는 구역 레이어(불가·주의 구역)를 처음 한 번 계산해 기억한다 → 복원 뒤에는 서버를 다시 켜야 새 데이터로 보인다

## 처음 해 보는 사람을 위한 순서 (처음부터 끝까지)

### 0. 한 번만 준비 (PC마다)

| 준비물 | 방법 |
| --- | --- |
| Python 3.10 이상 | `winget install Python.Python.3.12` → **VS Code 를 껐다 켜기** (안 하면 `python` 을 못 찾음). 그래도 "Microsoft Store" 안내가 나오면 윈도우 설정 → 앱 → 고급 앱 설정 → 앱 실행 별칭에서 `python.exe` · `python3.exe` 끄기 |
| PostgreSQL + PostGIS | PostgreSQL 설치 → StackBuilder 에서 PostGIS 설치 |
| 코드 | 팀 레포를 받고 `chabak` 브랜치 (머지 후에는 `main`) |
| 원본 데이터 | `지적도데이터.zip` (약 1.7GB). **깃에 없다** → 팀 공유 드라이브·USB 로 받아 `C:\chabak_data` 에 압축 풀기 (탐색기 오른쪽 클릭 → 압축 풀기) |

pgAdmin 에서 **내 컴퓨터**에 차박 DB 만들기 (팀 앱 DB · 공용 DB 와 이름이 달라서 영향 없음):

```sql
CREATE DATABASE chabak;
-- 만든 chabak DB 를 선택한 뒤 Query Tool 에서
CREATE EXTENSION postgis;
```

### 1. 실행 (VS Code 터미널 = PowerShell)

```powershell
cd C:\lx\safecamping-workspace\safecamping\db\chabak_pipeline   # 레포 위치가 다르면 바꾸기
pip install -r requirements.txt                                   # 처음 한 번만

chcp 65001
$env:PGHOST = "localhost"
$env:PGUSER = "postgres"
$env:PGDATABASE = "chabak"
$env:PGPASSWORD = "자기 로컬 postgres 비밀번호"

# ① 검사만 (DB 안 건드림, 1분 정도)
python pipeline.py --data "C:\chabak_data\지적도데이터" --steps extract,validate

# ② "검사 통과: 8개 데이터" 가 나오면 전부
python pipeline.py --data "C:\chabak_data\지적도데이터"
```

### 2. 끝나면 확인

- `reports/report_날짜.md` — 필지 **124,276** · 규제구역 **845** · 차박 후보지 **46** 이면 정상
- `chabak_날짜.dump` — 공용 DB 에 넣을 때 쓰는 파일 (깃에는 안 올라감)

### 주의

- PowerShell 창을 새로 열면 `$env:` 네 줄부터 다시 넣는다
- 데이터를 다른 곳에 풀었으면 `--data` 경로를 바꾼다
- 공용 DB 반영은 이 명령과 별개다 → 위 "공용 DB 반영" 절차대로, DB 주인과 상의한 뒤 사람이 직접
