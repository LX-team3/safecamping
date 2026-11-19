"""
차박 판정 DB 자동 구축 파이프라인

손으로 하던 순서를 그대로 자동화한다.
  1. extract   : 데이터 폴더의 zip 을 풀기 (이미 풀린 건 건너뜀)
  2. validate  : 파일 찾기 → 좌표계·인코딩·건수·PNU 검사 (문제 있으면 여기서 멈춤)
  3. load      : shp2pgsql 로 raw 스키마에 적재 (검사에서 찾은 인코딩으로)
  4. transform : 정리 SQL 실행 (02 → 03 → 05) → chabak 스키마 완성
  5. report    : 품질 리포트 (건수·매칭률·규제구역·판정 비율) + 지난번과 비교
  6. export    : chabak 스키마만 dump 로 묶기 (공용 DB 배포용)

사용 예
  python pipeline.py --data "C:/지적도데이터"                     # 전부
  python pipeline.py --data "C:/지적도데이터" --steps validate     # 검사만
  python pipeline.py --data "C:/지적도데이터" --steps report       # 리포트만

DB 접속 정보는 코드에 쓰지 않는다. psql 이 읽는 환경변수를 쓴다.
  PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE  (기본: localhost / postgres / chabak)
  PGBIN: PostgreSQL bin 폴더 (기본: C:/Program Files/PostgreSQL/18/bin)
※ 이 파이프라인은 내 컴퓨터 DB 용이다. 공용 DB 에는 export 로 만든 dump 만 넣는다.
"""

import argparse
import datetime as dt
import json
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

from dbfread import DBF

from datasets import DATASETS, SQL_STEPS, TARGET_SRID

HERE = Path(__file__).resolve().parent
SQL_DIR = HERE / "sql"
REPORT_DIR = HERE / "reports"
ALL_STEPS = ["extract", "validate", "load", "transform", "report", "export"]


# ---------------------------------------------------------------------
# 공통 도우미
# ---------------------------------------------------------------------
def log(msg: str) -> None:
    print(f"[{dt.datetime.now():%H:%M:%S}] {msg}", flush=True)


class PipelineError(Exception):
    """검사에 걸려서 더 진행하면 안 되는 경우"""


def pg_bin(tool: str) -> str:
    base = os.environ.get("PGBIN", r"C:\Program Files\PostgreSQL\18\bin")
    exe = Path(base) / (tool + (".exe" if os.name == "nt" else ""))
    return str(exe) if exe.exists() else tool  # PATH 에 있으면 그걸 씀


def pg_env() -> dict:
    env = os.environ.copy()
    env.setdefault("PGHOST", "localhost")
    env.setdefault("PGUSER", "postgres")
    env.setdefault("PGDATABASE", "chabak")
    env["PGCLIENTENCODING"] = "UTF8"  # 한글 SQL·결과가 깨지지 않게
    return env


def run(cmd, stdin=None, capture=False) -> str:
    """외부 명령 실행. 실패하면 에러 내용을 보여주고 멈춘다"""
    res = subprocess.run(cmd, input=stdin, capture_output=True, env=pg_env())
    out = res.stdout.decode("utf-8", "replace")
    err = res.stderr.decode("utf-8", "replace")
    if res.returncode != 0:
        raise PipelineError(f"명령 실패: {' '.join(map(str, cmd))}\n{err[-2000:]}")
    return out if capture else ""


def ensure_local(step: str) -> None:
    """
    DB 를 바꾸는 단계(load · transform)는 내 컴퓨터 DB 에서만 실행한다
    공용 DB 는 팀이 같이 쓰므로, PGHOST 를 공용 DB 로 둔 채 실수로 돌려도 여기서 멈춘다
    (공용 DB 에는 export 로 만든 dump 를 README "공용 DB 반영" 절차대로 직접 넣는다)
    """
    host = os.environ.get("PGHOST", "localhost")
    if host not in ("localhost", "127.0.0.1", "::1"):
        raise PipelineError(f"{step} 은(는) 내 컴퓨터 DB(localhost)에서만 실행합니다. 지금 PGHOST={host}. "
                            "공용 DB 에는 export 한 dump 를 README 절차대로 넣으세요.")


def psql_query(sql: str) -> list[dict]:
    """SELECT 결과를 dict 목록으로 (JSON 으로 받아서 파싱)"""
    # 별명을 _row 처럼 겹치지 않게 둔다: 쿼리 안에 t 라는 칸이 있으면 json_agg(t) 가 행이 아니라 그 칸만 묶는다
    # (2026-11-16 첫 실행에서 건수 리포트가 숫자 없이 테이블 이름만 나왔음)
    wrapped = f"SELECT COALESCE(json_agg(_row), '[]') FROM ({sql}) _row;"
    out = run([pg_bin("psql"), "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-c", wrapped], capture=True)
    return json.loads(out.strip() or "[]")


# ---------------------------------------------------------------------
# 1. extract : zip 풀기
# ---------------------------------------------------------------------
def zip_name(info: zipfile.ZipInfo) -> str:
    # 한국 공공데이터 zip 은 파일 이름이 CP949 인 경우가 많다
    if info.flag_bits & 0x800:
        return info.filename
    try:
        return info.filename.encode("cp437").decode("cp949")
    except UnicodeError:
        return info.filename


def step_extract(data_dir: Path) -> None:
    zips = sorted(data_dir.rglob("*.zip"))
    for zp in zips:
        target = zp.with_suffix("")
        if target.exists():
            continue
        log(f"압축 풀기: {zp.name}")
        with zipfile.ZipFile(zp) as z:
            for info in z.infolist():
                out = target / zip_name(info)
                if info.is_dir():
                    out.mkdir(parents=True, exist_ok=True)
                    continue
                out.parent.mkdir(parents=True, exist_ok=True)
                out.write_bytes(z.read(info))
    log(f"zip {len(zips)}개 확인 완료")


# ---------------------------------------------------------------------
# 2. validate : 좌표계·인코딩·건수·PNU
# ---------------------------------------------------------------------
def find_shp(data_dir: Path, pattern: str) -> Path | None:
    hits = sorted(data_dir.glob(pattern), key=lambda p: p.name)
    return hits[-1] if hits else None  # 이름에 날짜가 있어서 이름순 마지막 = 최신


def prj_params(prj_text: str) -> dict:
    return {k.lower(): float(v) for k, v in re.findall(r'PARAMETER\["([^"]+)",\s*([-0-9.]+)\]', prj_text)}


def detect_srid(prj: Path) -> int | None:
    """
    .prj 의 투영 값으로 좌표계 판단
    5186: 중부원점 127, 원점위도 38, 가산값 200,000 / 600,000 (GRS80)
    5174: 같은 원점이지만 가산값 500,000 (베셀 타원체, 옛 좌표계)
    """
    if not prj.exists():
        return None
    p = prj_params(prj.read_text(encoding="utf-8", errors="replace"))
    cm = p.get("central_meridian")
    lat0 = p.get("latitude_of_origin")
    fe, fn = p.get("false_easting"), p.get("false_northing")
    if cm and abs(cm - 127) < 0.01 and lat0 and abs(lat0 - 38) < 0.01:
        if fe == 200000 and fn == 600000:
            return 5186
        if fe == 200000 and fn == 500000:
            return 5174
    return None


def detect_encoding(dbf: Path, sample: int = 3000) -> str:
    """
    .cpg 를 믿지 않고 실제 글자로 판단한다
    (2026-11-14 토지특성정보: .cpg 는 UTF-8 인데 실제로는 EUC-KR 이었음)
    """
    table = DBF(str(dbf), raw=True, load=False)
    text_fields = [f.name for f in table.fields if f.type == "C"]
    utf8_ok = True
    has_non_ascii = False
    for i, rec in enumerate(table):
        if i >= sample:
            break
        for name in text_fields:
            b = rec[name]
            if not b or b.isascii():
                continue
            has_non_ascii = True
            try:
                b.decode("utf-8")
            except UnicodeDecodeError:
                utf8_ok = False
                break
        if not utf8_ok:
            break
    if not has_non_ascii:
        return "UTF-8"
    return "UTF-8" if utf8_ok else "EUC-KR"


def check_pnu(dbf: Path, field: str, encoding: str, sample: int = 5000) -> tuple[int, int]:
    """PNU 가 51810(인제군 신 코드)으로 시작하는 비율. 옛 코드 42810 이 섞이면 조인이 깨진다"""
    table = DBF(str(dbf), encoding="cp949" if encoding == "EUC-KR" else "utf-8",
                char_decode_errors="replace", load=False)
    ok = total = 0
    for i, rec in enumerate(table):
        if i >= sample:
            break
        v = str(rec.get(field) or "")
        total += 1
        ok += v.startswith("51810")
    return ok, total


def step_validate(data_dir: Path) -> list[dict]:
    plan, problems = [], []
    for ds in DATASETS:
        shp = find_shp(data_dir, ds["pattern"])
        if shp is None:
            problems.append(f"{ds['name']}: 파일을 찾지 못함 ({ds['pattern']})")
            continue
        dbf, prj = shp.with_suffix(".dbf"), shp.with_suffix(".prj")
        missing = [s for s in (".shx", ".dbf", ".prj") if not shp.with_suffix(s).exists()]
        if missing:
            problems.append(f"{ds['name']}: 세트 파일 없음 {missing} ({shp})")
            continue

        srid = detect_srid(prj)
        enc = detect_encoding(dbf)
        rows = len(DBF(str(dbf), load=False))
        item = dict(ds, shp=str(shp), srid=srid, encoding=enc, rows=rows)

        if srid != TARGET_SRID:
            problems.append(f"{ds['name']}: 좌표계 {srid or '알 수 없음'} (5186 이어야 함, 5174 버전을 받은 건 아닌지 확인)")
        if rows < ds["min_rows"]:
            problems.append(f"{ds['name']}: {rows:,}건 (최소 {ds['min_rows']:,}건 예상, 받다 만 파일인지 확인)")
        if ds["inje"]:
            ok, total = check_pnu(dbf, ds["pnu"], enc)
            item["pnu_ok_rate"] = round(ok / total * 100, 2) if total else 0
            if total and ok / total < 0.99:
                problems.append(f"{ds['name']}: PNU 가 51810 으로 시작하는 비율 {item['pnu_ok_rate']}% (옛 코드 42810 확인)")

        log(f"검사 {ds['name']:<18} {rows:>8,}건  좌표계 {srid}  인코딩 {enc}  ({shp.name})")
        plan.append(item)

    if problems:
        raise PipelineError("검사에서 문제 발견 → 진행 중단\n  - " + "\n  - ".join(problems))
    log(f"검사 통과: {len(plan)}개 데이터")
    return plan


# ---------------------------------------------------------------------
# 3. load : raw 적재
# ---------------------------------------------------------------------
def step_load(plan: list[dict]) -> None:
    ensure_local("load")  # raw 테이블을 지우고 다시 만드는 단계
    run([pg_bin("psql"), "-X", "-v", "ON_ERROR_STOP=1", "-c", "CREATE SCHEMA IF NOT EXISTS raw;"])
    for item in plan:
        table = f"raw.{item['table']}"
        log(f"적재 {item['name']} → {table}")
        # raw 는 원본 보관용이라 매번 새로 넣는다: 먼저 지우고(없으면 조용히 넘어감) → shp2pgsql -c 로 새로 만들기
        # (shp2pgsql -d 는 테이블이 이미 있다고 가정해서, 처음 적재하는 DB 에서는
        #  "column not found in geometry_columns table" 오류로 멈춘다 — 2026-11-16 첫 실행에서 겪음)
        run([pg_bin("psql"), "-X", "-q", "-v", "ON_ERROR_STOP=1", "-c", f"DROP TABLE IF EXISTS {table} CASCADE;"])
        sql = subprocess.run(
            [pg_bin("shp2pgsql"), "-c", "-s", str(TARGET_SRID), "-I", "-D", "-W", item["encoding"],
             item["shp"], table],
            capture_output=True, env=pg_env())
        if sql.returncode != 0:
            raise PipelineError(f"shp2pgsql 실패: {item['shp']}\n{sql.stderr.decode('utf-8', 'replace')[-2000:]}")
        run([pg_bin("psql"), "-X", "-q", "-v", "ON_ERROR_STOP=1"], stdin=sql.stdout)

        got = psql_query(f"SELECT COUNT(*) AS n FROM {table}")[0]["n"]
        if got != item["rows"]:
            raise PipelineError(f"{table}: 원본 {item['rows']:,}건인데 DB 에 {got:,}건 → 적재 확인 필요")
        log(f"  → {got:,}건 확인")


# ---------------------------------------------------------------------
# 4. transform : 정리 SQL
# ---------------------------------------------------------------------
def step_transform() -> None:
    # 정리 SQL 은 public 에 만들고 05 에서 chabak 으로 옮긴다
    # 이미 chabak 이 있으면 05 가 옮기지 않으므로, 내 컴퓨터 DB 에서는 먼저 비운다 (공용 DB 에서는 절대 실행하지 않음)
    ensure_local("transform")  # chabak 스키마를 지우고 다시 만드는 단계
    run([pg_bin("psql"), "-X", "-q", "-v", "ON_ERROR_STOP=1", "-c", "DROP SCHEMA IF EXISTS chabak CASCADE;"])
    for name in SQL_STEPS:
        log(f"SQL 실행: {name}")
        run([pg_bin("psql"), "-X", "-q", "-v", "ON_ERROR_STOP=1", "-P", "pager=off", "-f", str(SQL_DIR / name)],
            capture=True)
    log("정리 완료: chabak 스키마")


# ---------------------------------------------------------------------
# 5. report : 품질 리포트
# ---------------------------------------------------------------------
QUALITY_SQL = {
    "counts": """
        SELECT 'cadastre' AS t, COUNT(*) AS n FROM chabak.cadastre
        UNION ALL SELECT 'land_attr', COUNT(*) FROM chabak.land_attr
        UNION ALL SELECT 'regulation', COUNT(*) FROM chabak.regulation
        UNION ALL SELECT 'open_spot', COUNT(*) FROM chabak.open_spot
        UNION ALL SELECT 'campsite', COUNT(*) FROM chabak.campsite""",
    "match": """
        SELECT COUNT(*) FILTER (WHERE NOT src_lx)   AS lx_miss,
               COUNT(*) FILTER (WHERE NOT src_char) AS char_miss,
               COUNT(*)                             AS total
        FROM chabak.land_attr""",
    "regulation": """
        SELECT category, zone_name, level, COUNT(*) AS n
        FROM chabak.regulation GROUP BY 1, 2, 3 ORDER BY 1, 3, 2""",
    # 인제군 땅 위 무작위 점(최대 500개) → 판정 비율 (면적 기준)
    # ST_Extent 는 좌표계 번호를 잃는다(0) → ST_SetSRID 로 5186 을 다시 붙여야 필지와 비교 가능
    # (2026-11-16 첫 실행에서 "mixed SRID geometries" 오류로 멈춤)
    "verdict_by_area": """
        WITH ext AS (SELECT ST_SetSRID(ST_Extent(geom)::geometry, 5186) AS b FROM chabak.cadastre),
        pts AS (SELECT (ST_Dump(ST_GeneratePoints(b, 1200, 42))).geom AS g FROM ext),
        inside AS (SELECT p.g FROM pts p
                   WHERE EXISTS (SELECT 1 FROM chabak.cadastre c WHERE ST_Intersects(c.geom, p.g)) LIMIT 500)
        SELECT v.verdict, COUNT(*) AS n
        FROM inside, LATERAL (SELECT ST_Transform(g, 4326) AS w) t,
             LATERAL chabak.fn_verdict(ST_Y(w), ST_X(w)) v
        GROUP BY 1 ORDER BY 2 DESC""",
}


def step_report() -> Path:
    REPORT_DIR.mkdir(exist_ok=True)
    now = dt.datetime.now()
    result = {"run_at": now.isoformat(timespec="seconds")}
    for key, sql in QUALITY_SQL.items():
        log(f"리포트: {key}")
        result[key] = psql_query(sql)
    # 건수 표는 늘 같은 순서로 (UNION ALL 결과는 순서가 보장되지 않아 거꾸로 나온 적 있음)
    order = ["cadastre", "land_attr", "regulation", "open_spot", "campsite"]
    result["counts"].sort(key=lambda r: order.index(r["t"]) if r["t"] in order else len(order))

    # 지난 리포트와 비교 → 크게 바뀐 숫자에 경고
    prev_files = sorted(REPORT_DIR.glob("report_*.json"))
    prev = json.loads(prev_files[-1].read_text(encoding="utf-8")) if prev_files else None
    warnings = []
    m = result["match"][0]
    if m["total"] and m["lx_miss"] / m["total"] > 0.01:
        warnings.append(f"LX맵 매칭 누락 {m['lx_miss']:,}필지 (1% 초과)")
    if prev:
        before = {r["t"]: r["n"] for r in prev["counts"] if isinstance(r, dict)}  # 망가진 옛 리포트 줄은 건너뜀
        for r in result["counts"]:
            b = before.get(r["t"])
            if b and abs(r["n"] - b) / b > 0.05:
                warnings.append(f"{r['t']}: {b:,} → {r['n']:,} (5% 넘게 변함)")
    result["warnings"] = warnings

    stamp = f"{now:%Y%m%d_%H%M}"
    json_path = REPORT_DIR / f"report_{stamp}.json"
    json_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    md = [f"# 차박 판정 DB 품질 리포트 ({now:%Y-%m-%d %H:%M})", ""]
    md += ["## 건수", "", "| 테이블 | 건수 | 지난번 |", "| --- | --- | --- |"]
    before = {r["t"]: r["n"] for r in prev["counts"] if isinstance(r, dict)} if prev else {}
    for r in result["counts"]:
        b = before.get(r["t"])
        md.append(f"| {r['t']} | {r['n']:,} | {format(b, ',') if isinstance(b, int) else '-'} |")
    md += ["", "## 속성 매칭", "",
           f"- LX맵에 없는 필지: {m['lx_miss']:,} / {m['total']:,}",
           f"- 토지특성정보에 없는 필지: {m['char_miss']:,} / {m['total']:,} (기준일 차이)", ""]
    md += ["## 규제구역", "", "| 분류 | 구역 | 판정 | 건수 |", "| --- | --- | --- | --- |"]
    md += [f"| {r['category']} | {r['zone_name']} | {r['level']} | {r['n']} |" for r in result["regulation"]]
    # 인제군 범위 네모 안에 1200점을 찍어 인제군 땅 위에 떨어진 것만 쓴다 (최대 500) → 실제 개수를 제목에
    total = sum(r["n"] for r in result["verdict_by_area"]) or 1
    md += ["", f"## 판정 비율 (인제군 땅 위 무작위 {total}지점)", "", "| 판정 | 지점 | 비율 |", "| --- | --- | --- |"]
    md += [f"| {r['verdict']} | {r['n']} | {r['n'] / total * 100:.1f}% |" for r in result["verdict_by_area"]]
    md += ["", "## 경고", ""] + ([f"- {w}" for w in warnings] or ["- 없음"])

    md_path = REPORT_DIR / f"report_{stamp}.md"
    md_path.write_text("\n".join(md) + "\n", encoding="utf-8")
    log(f"리포트 저장: {md_path}")
    for w in warnings:
        log(f"⚠ {w}")
    return md_path


# ---------------------------------------------------------------------
# 6. export : 공용 DB 배포용 dump
# ---------------------------------------------------------------------
def step_export() -> Path:
    out = HERE / f"chabak_{dt.datetime.now():%Y%m%d}.dump"
    run([pg_bin("pg_dump"), "-n", "chabak", "-Fc", "-f", str(out)])
    log(f"dump 저장: {out} ({out.stat().st_size / 1024 / 1024:.1f}MB)")
    return out


# ---------------------------------------------------------------------
def main() -> int:
    ap = argparse.ArgumentParser(description="차박 판정 DB 자동 구축 파이프라인")
    ap.add_argument("--data", required=True, help="원본 데이터 폴더 (zip·shp 가 들어 있는 곳)")
    ap.add_argument("--steps", default=",".join(ALL_STEPS),
                    help=f"실행할 단계, 쉼표로 구분 (기본: 전부) {ALL_STEPS}")
    args = ap.parse_args()

    data_dir = Path(args.data)
    steps = [s.strip() for s in args.steps.split(",") if s.strip()]
    unknown = set(steps) - set(ALL_STEPS)
    if unknown:
        print(f"모르는 단계: {unknown}")
        return 2

    started = dt.datetime.now()
    try:
        if "extract" in steps:
            step_extract(data_dir)
        plan = None
        if "validate" in steps or "load" in steps:
            plan = step_validate(data_dir)
        if "load" in steps:
            step_load(plan)
        if "transform" in steps:
            step_transform()
        if "report" in steps:
            step_report()
        if "export" in steps:
            step_export()
    except PipelineError as e:
        log(f"중단: {e}")
        return 1
    log(f"끝 ({(dt.datetime.now() - started).seconds}초)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
