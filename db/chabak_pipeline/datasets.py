"""
파이프라인이 다루는 원본 데이터 목록.

새 데이터를 받으면 여기 한 줄만 추가하면 된다.
- pattern : 데이터 폴더 안에서 SHP 를 찾는 규칙 (여러 개면 파일 이름이 가장 최근인 것)
- table   : raw 스키마에 들어갈 테이블 이름
- inje    : True 면 인제군 파일 → PNU 가 51810 으로 시작하는지 검사
- pnu     : PNU 가 들어 있는 컬럼 이름 (inje=True 일 때만)
- min_rows: 이보다 적게 들어 있으면 받다 만 파일로 보고 멈춘다
"""

DATASETS = [
    # 필지 경계 (판정 기준)
    dict(name="연속지적도(인제)",   pattern="**/LSMD_CONT_LDREG_51810_*.shp", table="cadastre_src", inje=True,  pnu="PNU", min_rows=100_000),
    # 지목·소유 (연속지적도와 기준일이 같음)
    dict(name="LX맵 편집지적도(인제)", pattern="**/51810.shp",                 table="lx_map",       inje=True,  pnu="pnu", min_rows=100_000),
    # 토지이용상황·지형·도로접면
    dict(name="토지특성정보(인제)", pattern="**/AL_D194_51810_*.shp",          table="land_char",    inje=True,  pnu="A1",  min_rows=100_000),
    # 규제구역 (전국 파일 → 정리 단계에서 위치로 인제만 거름)
    dict(name="하천/용도구역",       pattern="**/AL_D079_00_*.shp",            table="river",        inje=False, min_rows=1_000),
    dict(name="자연공원/용도구역",   pattern="**/AL_D094_00_*.shp",            table="park",         inje=False, min_rows=100),
    dict(name="산림보호/산림보호",   pattern="**/AL_D320_00_*.shp",            table="forest",       inje=False, min_rows=1_000),
    dict(name="수도/상수원보호",     pattern="**/AL_D103_00_*.shp",            table="water",        inje=False, min_rows=100),
    dict(name="백두대간보호/용도지역", pattern="**/AL_D322_00_*.shp",          table="baekdu",       inje=False, min_rows=100),
]

# 정리 단계에서 순서대로 실행할 SQL (sql 폴더 기준)
SQL_STEPS = [
    "02_public_tables.sql",       # raw -> 정리 테이블 (cadastre, land_attr, regulation)
    "03_rules_and_verdict.sql",   # 규칙 테이블 + 판정 함수
    "05_move_to_chabak_schema.sql",  # chabak 스키마로 이동 + open_spot + JSON 함수
]

# 판정 기준 좌표계: 한국 2000 중부원점 (EPSG:5186)
TARGET_SRID = 5186
# .prj 안에 이 글자가 있으면 5186 으로 본다 (브이월드·LX맵 파일 기준)
SRID_5186_KEYWORDS = ["Central_Belt_2010", "Central Belt 2010", "EPSG\",\"5186"]
