package com.campin.dbtest;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;


import lombok.RequiredArgsConstructor;


// 서버 api 요청이 잘 들어오는지에 대한 컨트롤러입니다.

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {
	
	private final TestMapper testMapper;

    @GetMapping
    public Map<String, String> get() {
        return Map.of("message", "스프링 서버 연결 성공!");
    }

    @PostMapping
    public Map<String, Object> post(@RequestBody Map<String, Object> body) {
        return Map.of("received", body);
    }
    
    @GetMapping("/db")
    public String db() {
        return testMapper.now();
    }

    @GetMapping("/er/count")
    public int erCount() {
        return testMapper.countErDetail();
    }

    @GetMapping("/er")
    public List<Map<String, Object>> erList() {
        return testMapper.getErDetailList();
    }
}