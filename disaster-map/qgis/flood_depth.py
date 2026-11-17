# 침수 깊이 계산 (범람 범위 + 지형만으로 깊이를 추정하는 FwDET 방식)
# 생각: 범람 범위의 가장자리(물과 땅이 만나는 곳)의 땅 높이 = 그 자리 물 높이.
#       범위 안쪽 칸은 가장 가까운 가장자리의 물 높이를 받아오고(살짝 평균), 물 높이 - 땅 높이 = 깊이.
# QGIS Python 콘솔에서: exec(open(r"C:/Users/LX/Desktop/3d 재난지도 데이터/flood_depth.py", encoding="utf-8").read())
import collections, base64, json, os
import numpy as np
from osgeo import gdal, ogr, osr
from qgis.core import *

base = r"C:/Users/LX/Desktop/3d 재난지도 데이터"
P = QgsProject.instance()
dem = gdal.Open(base + "/수치자료/dem_10m_merged.tif")
gt = dem.GetGeoTransform(); Z = dem.GetRasterBand(1).ReadAsArray().astype(float)
H, W = Z.shape
crs = QgsCoordinateReferenceSystem("EPSG:5179")

def mask(layer, buffer_m=0, keep=None):
    srs = osr.SpatialReference(); srs.ImportFromEPSG(5179)
    ds = ogr.GetDriverByName('Memory').CreateDataSource('m'); lyr = ds.CreateLayer('l', srs)
    xf = QgsCoordinateTransform(layer.crs(), crs, P)
    for f in layer.getFeatures():
        if keep and not keep(f): continue
        g = QgsGeometry(f.geometry()); g.transform(xf)
        if buffer_m: g = g.buffer(buffer_m, 8)
        of = ogr.Feature(lyr.GetLayerDefn()); of.SetGeometry(ogr.CreateGeometryFromWkt(g.asWkt())); lyr.CreateFeature(of)
    out = gdal.GetDriverByName('MEM').Create('', W, H, 1, gdal.GDT_Byte)
    out.SetGeoTransform(gt); out.SetProjection(dem.GetProjection())
    gdal.RasterizeLayer(out, [1], lyr, burn_values=[1])
    return out.ReadAsArray() > 0

def box_mean(a, ok, r=2):
    # ok인 칸들만으로 (2r+1)x(2r+1) 평균
    v = np.where(ok, a, 0.0); n = ok.astype(float)
    pv = np.pad(v, r); pn = np.pad(n, r); sv = np.zeros_like(v); sn = np.zeros_like(n)
    for dy in range(2 * r + 1):
        for dx in range(2 * r + 1):
            sv += pv[dy:dy + H, dx:dx + W]; sn += pn[dy:dy + H, dx:dx + W]
    return np.where(sn > 0, sv / np.maximum(sn, 1), np.nan)

def depth_of(fm):
    valid = Z > -9000
    inside = fm & valid
    pad = np.pad(inside, 1, constant_values=False)
    nb_out = ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    edge = inside & nb_out
    # 가장 가까운 가장자리 칸의 땅 높이를 안쪽으로 퍼뜨리기 (BFS)
    wse = np.where(edge, Z, np.nan).tolist(); ins = inside.tolist()
    q = collections.deque(zip(*[a.tolist() for a in np.nonzero(edge)]))
    while q:
        y, x = q.popleft(); v = wse[y][x]
        for oy, ox in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            ny, nx = y + oy, x + ox
            if 0 <= ny < H and 0 <= nx < W and ins[ny][nx] and wse[ny][nx] != wse[ny][nx]:
                wse[ny][nx] = v; q.append((ny, nx))
    wse = np.array(wse)
    wse = box_mean(wse, inside & np.isfinite(wse), 2)      # 50m 범위 평균으로 들쭉날쭉한 물높이 다듬기
    d = np.where(inside, np.clip(wse - Z, 0, None), np.nan)
    return d

# 강 물길은 범람 범위에 빠져 있는 경우가 많아서, 물길 쪽 가장자리(강바닥 높이)가 물높이로 잡히지 않게 물길을 범위에 합쳐서 계산
river = mask(P.mapLayersByName('waterway')[0], 25, lambda f: f['waterway'] == 'river')
out = {}
for yr in ('50', '80', '100'):
    fm = mask(P.mapLayersByName(f'범람 {yr}년 빈도')[0])
    d = depth_of(fm | river)
    d = np.where(fm, d, np.nan)
    path = base + f"/하천범람지도/depth_{yr}.tif"
    o = gdal.GetDriverByName('GTiff').Create(path, W, H, 1, gdal.GDT_Float32, ['COMPRESS=DEFLATE'])
    o.SetGeoTransform(gt); o.SetProjection(dem.GetProjection())
    b = o.GetRasterBand(1); b.WriteArray(np.nan_to_num(d, nan=-1).astype(np.float32)); b.SetNoDataValue(-1); o = None
    v = d[np.isfinite(d)]
    out[yr] = path
    print(f'{yr}년: 칸 {v.size}개, 깊이 중간값 {np.median(v):.2f}m, 90% {np.percentile(v, 90):.2f}m, 0.5m 미만 {np.mean(v < 0.5) * 100:.0f}%, 2m 이상 {np.mean(v >= 2) * 100:.0f}%')

pl = P.mapLayersByName('points (1) — points')[0] if P.mapLayersByName('points (1) — points') else P.mapLayersByName('points')[0]
xf = QgsCoordinateTransform(pl.crs(), crs, P)
for f in pl.getFeatures():
    p = xf.transform(f.geometry().asPoint()); cx = int((p.x() - gt[0]) / gt[1]); cy = int((p.y() - gt[3]) / gt[5])
    vals = []
    for yr in ('50', '80', '100'):
        ds_ = gdal.Open(out[yr]); a = ds_.GetRasterBand(1).ReadAsArray(); ds_ = None; vals.append(round(float(a[cy, cx]), 2))
    print(f['name'], '깊이(50/80/100년)', vals)
