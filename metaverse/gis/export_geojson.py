# camp.gpkg의 모든 레이어를 게임용 GeoJSON으로 한 번에 내보내요.
#
# 쓰는 방법 (QGIS에서 camp.qgz를 연 상태로)
#   1. 고친 레이어를 먼저 저장해요 (저장 아이콘). 저장하지 않은 내용은 내보내지지 않아요
#   2. 플러그인 → 파이썬 콘솔 (Ctrl+Alt+P) → 편집기 보기
#   3. 이 파일(gis/export_geojson.py)을 열고 ▶ 실행
#   4. 브라우저에서 게임을 새로고침
#
# camp.gpkg는 읽기만 해요. 바뀌는 것은 client/public/metaverse/data/ 안의 .geojson 파일뿐이에요.
# (메타버스가 client 안의 화면으로 합쳐져서 데이터도 그쪽으로 옮김. 2026-11-22)
import os

from osgeo import ogr
from qgis.core import (
    QgsCoordinateReferenceSystem,
    QgsCoordinateTransform,
    QgsProject,
    QgsVectorFileWriter,
    QgsVectorLayer,
)

ogr.UseExceptions()  # 파일을 못 열면 조용히 넘어가지 않고 오류로 알려 줘요

# ---------- 설정 ----------
CRS = 'EPSG:5179'  # 게임이 쓰는 좌표계
LAYER_OPTIONS = [
    'COORDINATE_PRECISION=1',  # 소수점 1자리 (10cm 단위)
    'RFC7946=NO',              # NO여야 EPSG:5179 좌표 그대로 나와요 (YES면 위경도로 바뀌어요)
]
SKIP = []  # 내보내지 않을 레이어 이름. 예: ['test']


def layer_names(gpkg):
    """gpkg 안에 있는 벡터 레이어 이름 목록"""
    ds = ogr.Open(gpkg)  # 읽기 전용으로 열어요
    if ds is None:
        raise RuntimeError(f'파일을 열 수 없어요: {gpkg}')
    names = [ds.GetLayerByIndex(i).GetName() for i in range(ds.GetLayerCount())]
    ds = None  # 파일 닫기
    return names


def export_layer(gpkg, name, out_dir):
    """레이어 하나를 out_dir/이름.geojson으로 내보내요. 돌려주는 값: 피처 개수"""
    layer = QgsVectorLayer(f'{gpkg}|layername={name}', name, 'ogr')
    if not layer.isValid():
        raise RuntimeError('레이어를 읽을 수 없어요')

    options = QgsVectorFileWriter.SaveVectorOptions()
    options.driverName = 'GeoJSON'
    options.fileEncoding = 'UTF-8'
    options.layerName = name
    options.layerOptions = LAYER_OPTIONS
    # 레이어 좌표계가 다르면 EPSG:5179로 바꿔서 내보내요
    target = QgsCoordinateReferenceSystem(CRS)
    context = QgsProject.instance().transformContext()
    if layer.crs() != target:
        options.ct = QgsCoordinateTransform(layer.crs(), target, context)

    path = os.path.join(out_dir, f'{name}.geojson')  # 같은 이름이 있으면 덮어써요
    result = QgsVectorFileWriter.writeAsVectorFormatV3(layer, path, context, options)
    if result[0] != QgsVectorFileWriter.NoError:
        raise RuntimeError(result[1])
    return layer.featureCount()


def export_all(gpkg, out_dir):
    """모든 레이어 내보내기. 돌려주는 값: (성공한 개수, 실패한 개수)"""
    if not os.path.isdir(out_dir):
        raise RuntimeError(f'내보낼 폴더가 없어요: {out_dir}')
    ok = 0
    failed = 0
    for name in layer_names(gpkg):
        if name in SKIP:
            print(f'  건너뜀  {name}')
            continue
        try:
            count = export_layer(gpkg, name, out_dir)
            print(f'  완료    {name}.geojson  ({count}개)')
            ok += 1
        except Exception as err:  # 하나가 실패해도 나머지는 계속 내보내요
            print(f'  실패    {name}: {err}')
            failed += 1
    print(f'끝: {ok}개 내보냄, {failed}개 실패 → {out_dir}')
    return ok, failed


def unsaved_layers():
    """편집 중인데 아직 저장하지 않은 레이어 이름 목록"""
    return [
        layer.name()
        for layer in QgsProject.instance().mapLayers().values()
        if isinstance(layer, QgsVectorLayer) and layer.isModified()
    ]


def run():
    # camp.qgz가 있는 폴더(gis/)를 기준으로 경로를 정해요
    gis_dir = QgsProject.instance().homePath()
    if not gis_dir:
        print('먼저 QGIS에서 camp.qgz 프로젝트를 열어 주세요.')
        return
    gpkg = os.path.join(gis_dir, 'camp.gpkg')
    # gis 폴더(metaverse/gis) 기준으로 두 칸 위가 프로젝트 맨 위 → client/public/metaverse/data
    out_dir = os.path.normpath(os.path.join(gis_dir, '..', '..', 'client', 'public', 'metaverse', 'data'))

    unsaved = unsaved_layers()
    if unsaved:
        print('저장하지 않은 레이어가 있어요:', ', '.join(unsaved))
        print('→ 저장 아이콘을 누른 뒤 다시 실행해 주세요. (저장 안 한 내용은 내보내지지 않아요)')
        return
    export_all(gpkg, out_dir)


run()
