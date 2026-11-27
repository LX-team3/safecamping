package com.campin.family;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// family_location 테이블 SQL. 테이블 정의는 db/schema_05.sql
// "이 회원이 이 방 사람인가" 확인은 FamilyService.requireMyRoom 이 먼저 하고, 여기 SQL 은 방 번호로만 찾는다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface LocationMapper {

    // 이 방 구성원 중 위치가 있는 사람만 (위치 줄이 없는 사람은 JOIN 에서 빠진다)
    // 방을 나간 사람은 family_member 에 없으므로 위치가 남아 있어도 보이지 않는다
    // 순서는 구성원 목록과 같게 먼저 들어온 순서
    // seq = 방에 들어온 순번(1부터). 화면이 구성원 이름 칩과 지도 마커를 같은 색으로 칠할 때 쓴다
    //   위치 줄과 JOIN 하기 "전에" 방 구성원 전체로 번호를 매긴다 → 구성원 목록(FamilyMapper.findMembers)의 순서와 같아지고,
    //   앞사람이 위치를 켜고 꺼도 내 번호(색)가 바뀌지 않는다
    @Select("""
            SELECT r.memberId, m.name, l.lon, l.lat, l.updated_at AS updatedAt, r.seq
              FROM (SELECT fm.member_id AS memberId,
                           ROW_NUMBER() OVER (ORDER BY fm.joined_at, fm.id) AS seq
                      FROM family_member fm
                     WHERE fm.family_room_id = #{roomId}) r
              JOIN member m ON m.id = r.memberId
              JOIN family_location l ON l.member_id = r.memberId
             ORDER BY r.seq
            """)
    List<FamilyLocation> findAll(@Param("roomId") Long roomId);

    // 내 위치 저장. 회원당 한 줄(member_id UNIQUE)이라 이미 있으면 좌표·시각만 바꾼다
    @Insert("""
            INSERT INTO family_location (member_id, lon, lat) VALUES (#{memberId}, #{lon}, #{lat})
            ON CONFLICT (member_id) DO UPDATE SET lon = EXCLUDED.lon, lat = EXCLUDED.lat, updated_at = now()
            """)
    int upsert(@Param("memberId") Long memberId, @Param("lon") Double lon, @Param("lat") Double lat);

    // 위치 공유 끄기: 내 줄을 지운다 → 가족에게 더 이상 보이지 않고, 마지막 위치도 남기지 않는다
    @Delete("DELETE FROM family_location WHERE member_id = #{memberId}")
    int delete(@Param("memberId") Long memberId);
}
