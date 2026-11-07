package com.campin.region;

import java.util.List;

import org.springframework.stereotype.Service;

import com.campin.common.InvalidRequestException;

import lombok.RequiredArgsConstructor;

// 탐색 탭 지역 선택 시트: 전국 시·도 → 시·군·구
@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionMapper regionMapper;

    public List<SidoResponse> sidos() {
        return regionMapper.findSidos();
    }

    public List<SigunguResponse> sigungus(Long sidoId) {
        if (!regionMapper.existsSido(sidoId)) {
            throw new InvalidRequestException("없는 시·도입니다.");
        }
        return regionMapper.findSigungus(sidoId);
    }

    // 검색어가 비면 빈 목록 (전체를 내려 주지 않는다)
    public List<SigunguResponse> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return regionMapper.searchSigungus(q.trim());
    }
}
