package com.madfinder.server.repository;

/**
 * tier_entries 접근 계층. (담당: KJH — 쿼리 전부 여기에)
 * 주요: user_id로 티어표 조회(ORDER BY tier,position), user_id 전체 삭제+재삽입(트랜잭션)
 * TODO(KJH): JpaRepository 상속으로 전환 후 쿼리 메서드/@Query 작성.
 */
public interface TierEntryRepository {
}
