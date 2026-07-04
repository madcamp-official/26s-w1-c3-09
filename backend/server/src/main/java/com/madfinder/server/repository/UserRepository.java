package com.madfinder.server.repository;

import com.madfinder.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * users 접근 계층.
 * 닉네임 → ID 캐시(F-5: 한 번 변환한 닉네임은 재변환 안 함 — username 병목 회피).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 닉네임 재검색 시 DB 히트 (로블록스 username API 호출 생략) */
    Optional<User> findByUsernameIgnoreCase(String username);
}
