# QGIS Python 콘솔에서 실행: 프로젝트 레이어를 Cesium 뷰어용 data.js로 내보냄
import os, json, base64, numpy as np
from osgeo import gdal
from qgis.core import *
from qgis.PyQt.QtCore import QVariant

base = r"C:/Users/LX/Desktop/3d 재난지도 데이터"
out = base + "/cesium_viewer"; os.makedirs(out, exist_ok=True)
P = QgsProject.instance()
def L(name): return [l for l in P.mapLayers().values() if l.name() == name][0]
wgs = QgsCoordinateReferenceSystem("EPSG:4326")
D = {}

# --- DEM -> EPSG:4326 높이 격자
def grid(src, res):
    ds = gdal.Warp('', src, format='MEM', dstSRS='EPSG:4326', xRes=res, yRes=res,
                   resampleAlg='bilinear', dstNodata=-9999)
    gt = ds.GetGeoTransform(); a = ds.GetRasterBand(1).ReadAsArray().astype(np.float32)
    ok = a > -9000
    return dict(w=ds.RasterXSize, h=ds.RasterYSize, west=gt[0], north=gt[3], dx=gt[1], dy=-gt[5],
                min=float(a[ok].min()), max=float(a[ok].max()),
                data=base64.b64encode(a.tobytes()).decode())
D['demFine'] = grid(base + "/수치자료/dem_10m_merged.tif", 0.0001)
D['demCoarse'] = grid(base + "/dem.tif", 0.001)

# --- 위성영상 -> JPEG
ds = gdal.Warp('', base + "/sat.tif", format='MEM', dstSRS='EPSG:4326', resampleAlg='bilinear')
gt = ds.GetGeoTransform()
gdal.Translate('/vsimem/sat.jpg', ds, format='JPEG', bandList=[1, 2, 3], creationOptions=['QUALITY=82'])
f = gdal.VSIFOpenL('/vsimem/sat.jpg', 'rb'); gdal.VSIFSeekL(f, 0, 2); n = gdal.VSIFTellL(f); gdal.VSIFSeekL(f, 0, 0)
jpg = gdal.VSIFReadL(1, n, f); gdal.VSIFCloseL(f); gdal.Unlink('/vsimem/sat.jpg')
D['sat'] = dict(west=gt[0], north=gt[3], east=gt[0] + gt[1] * ds.RasterXSize,
                south=gt[3] + gt[5] * ds.RasterYSize,
                url='data:image/jpeg;base64,' + base64.b64encode(jpg).decode())

# --- 벡터
demF = L('dem_10m (1:5000+1:25000 TIN)'); demC = L('dem')
def elev(pt):
    for d in (demF, demC):
        v, ok = d.dataProvider().sample(pt, 1)
        if ok and v == v and v > -9000: return float(v)
    return None

def clean(v):
    if v is None or isinstance(v, QVariant) or (hasattr(v, 'isNull') and v.isNull()): return None
    if hasattr(v, 'toString'): return v.toString()
    return v

def fc(layer, props, extra=None):
    xf = QgsCoordinateTransform(layer.crs(), wgs, P)
    to5179 = QgsCoordinateTransform(layer.crs(), demF.crs(), P)
    feats = []
    for f in layer.getFeatures():
        g = QgsGeometry(f.geometry())
        if g.isEmpty(): continue
        pr = {k: clean(f[k]) for k in props}
        if extra: pr.update(extra(f, g, to5179))
        g.transform(xf)
        feats.append({"type": "Feature", "properties": pr, "geometry": json.loads(g.asJson(7))})
    return {"type": "FeatureCollection", "features": feats}

def base_z(g, t):
    gg = QgsGeometry(g); gg.transform(t)
    zs = [z for z in (elev(QgsPointXY(v.x(), v.y())) for v in gg.vertices()) if z is not None]
    return round(min(zs) - 0.5, 2) if zs else 0

def num(v):
    try: return float(v)
    except Exception: return 0.0

def bld_extra(f, g, t):
    h = num(f['A16'])                        # 건물 높이(m)
    if h <= 0: h = num(f['A26']) * 3.3       # 지상 층수 x 3.3m
    if h <= 0: h = 4
    return {"base": base_z(g, t), "hgt": round(h, 2)}

D['buildings'] = fc(L('buildings.kangwon — buildings'), ['A9', 'A11', 'A16', 'A24', 'A25', 'A26', 'A4'], bld_extra)
D['osmBuildings'] = fc(L('building'), ['name', 'amenity', 'building'],
                       lambda f, g, t: {"base": base_z(g, t), "hgt": 6})
for k, n in [('flood50', '범람 50년 빈도'), ('flood80', '범람 80년 빈도'), ('flood100', '범람 100년 빈도')]:
    D[k] = fc(L(n), ['SEG_CODE', 'FLDLV_FREQ'])
D['evac'] = fc(L('evac_route (대피 경로)'), ['camp_name', 'disaster', 'length_m', 'alert', 'dest', 'bridge'])
D['points'] = fc(L('points (1) — points'), ['layer', 'name', 'level', 'capacity', 'address', 'flood_ok', 'coord_note'],
                 lambda f, g, t: {"z": elev(t.transform(g.asPoint()))})
D['waterway'] = fc(L('waterway'), ['name', 'waterway'])
D['roads'] = fc(L('highroad'), ['name', 'highway', 'bridge'])

# --- 100년 빈도 범람구역 안의 지반고 범위 (수위 슬라이더 범위)
lay = L('범람 100년 빈도'); t = QgsCoordinateTransform(lay.crs(), demF.crs(), P)
geom = QgsGeometry.unaryUnion([QgsGeometry(f.geometry()) for f in lay.getFeatures()]); geom.transform(t)
eng = QgsGeometry.createGeometryEngine(geom.constGet()); eng.prepareGeometry()
r = gdal.Open(base + "/수치자료/dem_10m_merged.tif"); a = r.GetRasterBand(1).ReadAsArray(); gt = r.GetGeoTransform()
zs = []
for j in range(0, a.shape[0], 2):
    for i in range(0, a.shape[1], 2):
        if a[j, i] > -9000 and eng.contains(QgsPoint(gt[0] + (i + .5) * gt[1], gt[3] + (j + .5) * gt[5])):
            zs.append(float(a[j, i]))
D['floodZ'] = dict(min=min(zs), max=max(zs), p10=float(np.percentile(zs, 10)), p90=float(np.percentile(zs, 90)))

s = "window.DATA = " + json.dumps(D, ensure_ascii=False, default=str) + ";"
open(out + "/data.js", "w", encoding="utf-8").write(s)
# 모바일 웹앱(app 폴더)에도 같은 데이터를 넣음. 배포 후에는 app/sw.js의 VERSION을 올려야 폰에 새 데이터가 반영됨
os.makedirs(base + "/app", exist_ok=True)
open(base + "/app/data.js", "w", encoding="utf-8").write(s)
print({k: len(v['features']) for k, v in D.items() if isinstance(v, dict) and 'features' in v})
print('points', [(p['properties']['layer'], p['properties']['name'], p['properties']['z']) for p in D['points']['features']])
print('floodZ', D['floodZ'], 'dem', D['demFine']['min'], D['demFine']['max'])
print('data.js', len(s) // 1024, 'KB')
