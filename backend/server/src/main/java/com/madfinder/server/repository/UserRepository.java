package com.madfinder.server.repository;

/**
 * users 접근 계층. (담당: KJH — 쿼리 전부 여기에)
 * 주요: user_id 단건, UPSERT(방문 시 last_seen_at 갱신)
 * TODO(KJH): JpaRepository 상속으로 전환 후 쿼리 메서드/@Query 작성.
 */
public interface UserRepository {
}
