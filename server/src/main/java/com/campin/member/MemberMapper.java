package com.campin.member;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


// member 테이블 SQL. 테이블 정의는 db/schema.sql
@Mapper  // MyBatis: 이 인터페이스의 구현체를 자동으로 만들어 스프링 빈으로 등록한다
public interface MemberMapper {

    // 컬럼은 snake_case, 자바 필드는 camelCase 라서 AS 로 이름을 맞춘다
    @Select("""
            SELECT id, login_id AS loginId, password_hash AS passwordHash, name,
                   phone, emergency_phone AS emergencyPhone,
                   address, address_detail AS addressDetail, created_at AS createdAt
              FROM member
             WHERE login_id = #{loginId}
            """)
    Member findByLoginId(@Param("loginId") String loginId);

    @Select("SELECT EXISTS (SELECT 1 FROM member WHERE login_id = #{loginId})")
    boolean existsByLoginId(@Param("loginId") String loginId);

    // @Options: INSERT 후 DB 가 만든 id 를 member.id 에 다시 채워 준다
    @Insert("""
            INSERT INTO member (login_id, password_hash, name, phone, emergency_phone, address, address_detail)
            VALUES (#{loginId}, #{passwordHash}, #{name}, #{phone}, #{emergencyPhone}, #{address}, #{addressDetail})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(Member member);
}
