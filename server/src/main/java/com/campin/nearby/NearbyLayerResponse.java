package com.campin.nearby;

// GET /api/nearby/layers 한 줄. on: 처음 화면을 열 때 켜져 있는지 (데이터가 있는 것만 켠다)
public record NearbyLayerResponse(String code, String icon, String label, String desc, boolean ready, boolean on) {

    static NearbyLayerResponse from(NearbyLayer l) {
        return new NearbyLayerResponse(l.code(), l.icon(), l.label(), l.desc(), l.ready(), l.ready());
    }
}
