package com.campin.family;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.campin.member.Member;

// family_room·family_member·meeting_place 테이블 SQL. 테이블 정의는 db/schema.sql
// "이 회원이 이 방 사람인가" 확인은 FamilyService 가 먼저 하고, 여기 SQL 은 방 번호로만 찾는다
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface FamilyMapper {

    // 회원이 들어가 있는 방. 없으면 null
    @Select("""
            SELECT r.id, r.name, r.invite_code AS inviteCode, r.created_at AS createdAt
              FROM family_room r
              JOIN family_member fm ON fm.family_room_id = r.id
             WHERE fm.member_id = #{memberId}
            """)
    FamilyRoom findRoomByMember(@Param("memberId") Long memberId);

    @Select("""
            SELECT id, name, invite_code AS inviteCode, created_at AS createdAt
              FROM family_room
             WHERE invite_code = #{inviteCode}
            """)
    FamilyRoom findRoomByInviteCode(@Param("inviteCode") String inviteCode);

    // 구성원: 먼저 들어온 순서대로. 이름 표시에 필요한 id·name 만 읽는다
    @Select("""
            SELECT m.id, m.name
              FROM family_member fm
              JOIN member m ON m.id = fm.member_id
             WHERE fm.family_room_id = #{roomId}
             ORDER BY fm.joined_at, fm.id
            """)
    List<Member> findMembers(@Param("roomId") Long roomId);

    // @Options: INSERT 후 DB 가 만든 id 를 room.id 에 다시 채워 준다
    // ON CONFLICT: 초대코드가 우연히 겹치면 오류 대신 0줄 → 서비스가 새 코드로 다시 시도
    @Insert("""
            INSERT INTO family_room (name, invite_code) VALUES (#{name}, #{inviteCode})
            ON CONFLICT (invite_code) DO NOTHING
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertRoom(FamilyRoom room);

    // 이미 다른 방에 들어가 있으면(member_id UNIQUE) 0줄 → 서비스가 "이미 가족방이 있습니다" 로 알린다
    @Insert("""
            INSERT INTO family_member (family_room_id, member_id) VALUES (#{roomId}, #{memberId})
            ON CONFLICT (member_id) DO NOTHING
            """)
    int insertMember(@Param("roomId") Long roomId, @Param("memberId") Long memberId);

    @Delete("DELETE FROM family_member WHERE member_id = #{memberId}")
    int deleteMember(@Param("memberId") Long memberId);

    // 아무도 없는 방만 지운다 (나가는 순간 다른 사람이 들어왔으면 지우지 않음). 모일 장소는 ON DELETE CASCADE
    @Delete("""
            DELETE FROM family_room r
             WHERE r.id = #{roomId}
               AND NOT EXISTS (SELECT 1 FROM family_member fm WHERE fm.family_room_id = r.id)
            """)
    int deleteRoomIfEmpty(@Param("roomId") Long roomId);

    // 모일 장소: 먼저 지정한 순서대로
    @Select("""
            SELECT p.id, p.family_room_id AS familyRoomId, p.name, p.description, p.lon, p.lat,
                   p.created_by AS createdBy, m.name AS createdByName, p.created_at AS createdAt
              FROM meeting_place p
              LEFT JOIN member m ON m.id = p.created_by
             WHERE p.family_room_id = #{roomId}
             ORDER BY p.created_at, p.id
            """)
    List<MeetingPlace> findPlaces(@Param("roomId") Long roomId);

    @Insert("""
            INSERT INTO meeting_place (family_room_id, name, description, lon, lat, created_by)
            VALUES (#{familyRoomId}, #{name}, #{description}, #{lon}, #{lat}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertPlace(MeetingPlace place);

    // 방 번호 조건을 함께 붙인다 → 다른 방의 장소 id 를 보내도 지워지지 않는다 (0줄)
    @Delete("DELETE FROM meeting_place WHERE id = #{placeId} AND family_room_id = #{roomId}")
    int deletePlace(@Param("roomId") Long roomId, @Param("placeId") Long placeId);
}
