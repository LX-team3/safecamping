# 재난지도 70·80·90·100·110mm 때 강 구역이 차는 순서 만들기 (data.json 의 flood50fill → floodFillSteps)
# 강 구역 = 공식 범람지도가 빼놓은 강 쪽 낮은 땅(flood50fill, 10m 칸). 그 밖(마을·캠핑장 쪽)은 114mm 이상의 공식 50년 범위에서만 잠긴다
# 순서 = 강보다 높은 정도(강에서 이어지는 길의 가장 높은 턱, 10m 지형) + 강에서 먼 정도. 낮고 가까운 땅이 먼저다
# 켜는 양(강물 밖 보이는 칸의 8·18·40·70·100%)은 보기 좋게 정한 값이다. 비 몇 mm 에 어디까지 차는지 계산한 것이 아니다
# 실행: python disaster-map/qgis/flood_steps.py client/public/disaster-map/data.json
import json, math, base64, heapq, struct, sys
txt = open(sys.argv[1], encoding='utf-8').read(); d = json.loads(txt)
assert 'floodFillSteps' not in d, '이미 있음: data.json 에서 floodFillSteps 를 빼고 다시'
g = d['demFine']; raw = base64.b64decode(g['data']); dem = struct.unpack('<%df' % (len(raw) // 4), raw)
S = 1e-4; MX = 111320 * math.cos(math.radians(38.225)); MY = 110540
def z(x, y):
    c = int((x - g['west']) / g['dx']); r = int((g['north'] - y) / g['dy'])
    v = dem[min(max(r, 0), g['h'] - 1) * g['w'] + min(max(c, 0), g['w'] - 1)]
    return v if v > -9000 else None
def lines(q): return [q['coordinates']] if q['type'] == 'LineString' else q['coordinates']
def dense(co, step):     # 선을 step m 마다 점으로
    o = []
    for a, b in zip(co, co[1:]):
        n = max(1, math.ceil(math.hypot((b[0] - a[0]) * MX, (b[1] - a[1]) * MY) / step))
        o += [(a[0] + (b[0] - a[0]) * t / n, a[1] + (b[1] - a[1]) * t / n) for t in range(n + 1)]
    return o
R = d['flood50fill']; x0, y0 = R[0][0], R[0][1]; cells = {}
for w, s, e, n in R:
    i0 = round((w - x0) / S); j = round((s - y0) / S)
    for m in range(round((n - s) / S)):
        for k in range(round((e - w) / S)): cells[(i0 + k, j + m)] = (w + (k + .5) * S, s + (m + .5) * S)
river = [(x, y, z(x, y)) for f in d['waterway']['features'] if f['properties']['waterway'] == 'river' for l in lines(f['geometry']) for x, y in dense(l, 10)]
river = [r for r in river if r[2] is not None]
rel, dist = {}, {}
for c, (x, y) in cells.items():
    b = min(river, key=lambda r: ((r[0] - x) * MX) ** 2 + ((r[1] - y) * MY) ** 2)
    dist[c] = math.hypot((b[0] - x) * MX, (b[1] - y) * MY); v = z(x, y); rel[c] = v - b[2] if v is not None else 99
best = {}; pq = [(max(rel[c], 0), c) for c in cells if dist[c] <= 31]; heapq.heapify(pq)
while pq:
    v, c = heapq.heappop(pq)
    if c in best: continue
    best[c] = v
    for a in (-1, 0, 1):
        for b in (-1, 0, 1):
            k = (c[0] + a, c[1] + b)
            if k in cells and k not in best: heapq.heappush(pq, (max(v, rel[k]), k))
score = {c: dist[c] + 15 * min(best.get(c, 20), 20) for c in cells}    # 1m 높은 것을 15m 먼 것만큼으로
def smooth(c):       # 주변 7x7칸 평균: 물이 얼룩덜룩하지 않게
    v = [score[k] for k in ((c[0] + a, c[1] + b) for a in range(-3, 4) for b in range(-3, 4)) if k in score]
    return sum(v) / len(v)
sm = {c: smooth(c) for c in cells}; order = sorted(cells, key=lambda c: sm[c])
# 준비 단계(60~85mm)에는 대피소 길을 안내하므로 그 길 20m 안의 칸은 90mm 로 미룬다
base = [p for f in d['evac']['features'] if '없음' not in (f['properties'].get('bridge') or '없음') for l in lines(f['geometry']) for p in dense(l, 5)]
def near(c):
    x, y = cells[c]
    return any(((p[0] - x) * MX) ** 2 + ((p[1] - y) * MY) ** 2 < 400 for p in base)
NEAR = {c for c in cells if near(c)}
inner = [c for c in order if dist[c] < 24]        # 준비 단계 강 절반 폭(약 24m) 안은 이미 강물이라 안 보인다
shown = [c for c in order if dist[c] >= 24]; ok = [c for c in shown if c not in NEAR]; m = len(shown)
c70, c80 = int(m * .08), int(m * .18)
a70 = [c for c in inner if c not in NEAR] + ok[:c70]; a80 = ok[c70:c80]
a90 = [c for c in inner if c in NEAR] + [c for c in shown if c in NEAR] + ok[c80:c80 + m * 22 // 100]
used = set(a70) | set(a80) | set(a90); r2 = [c for c in order if c not in used]; k = m * 30 // 100
steps = []
for part in (a70, a80, a90, r2[:k], r2[k:]):
    part = sorted(part, key=lambda c: (c[1], c[0])); out = []; i = 0
    while i < len(part):
        j = i
        while j + 1 < len(part) and part[j + 1] == (part[j][0] + 1, part[j][1]): j += 1
        a, b = part[i], part[j]
        out.append([round(x0 + a[0] * S, 7), round(y0 + a[1] * S, 7), round(x0 + (b[0] + 1) * S, 7), round(y0 + (a[1] + 1) * S, 7)]); i = j + 1
    steps.append(out); print('칸', len(part), '띠', len(out))
P = lambda p: (math.floor((p[0] - x0) / S + 1e-6), math.floor((p[1] - y0) / S + 1e-6))
hill = dense([[128.2060817, 38.2252365], [128.2060194, 38.2252209], [128.2055194, 38.2252209], [128.2055194, 38.2247209]], 2)
early = set(a70) | set(a80)
print('전체', len(cells), '= 합', sum(map(len, (a70, a80, a90, r2))), '· 높은 곳 길 겹침', sum(P(p) in cells for p in hill), '· 70·80mm 와 대피소 길 겹침', sum(P(p) in early for p in base))
body = txt.rstrip(); assert body.endswith('}')
open(sys.argv[1], 'w', encoding='utf-8', newline='').write(body[:-1] + ', "floodFillSteps": ' + json.dumps(steps) + '}' + txt[len(body):])
