package com.campin.family;

// 모일 장소 카드 하나. lon·lat 이 null 이면 화면에서 "지도에서 보기"를 숨긴다
public record MeetingPlaceResponse(Long id, String name, String description,
                                   Double lon, Double lat, String createdByName) {

    public static MeetingPlaceResponse from(MeetingPlace p) {
        return new MeetingPlaceResponse(p.getId(), p.getName(), p.getDescription(),
                p.getLon(), p.getLat(), p.getCreatedByName());
    }
}
