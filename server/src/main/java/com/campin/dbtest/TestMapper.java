package com.campin.dbtest;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

// db 연결에 관한 mapper 테스트입니다.

@Mapper
public interface TestMapper {

    @Select("select now()")
    String now();

    @Select("select count(*) from er_detail")
    int countErDetail();

    @Select("select * from er_detail limit 10")
    List<Map<String, Object>> getErDetailList();
}