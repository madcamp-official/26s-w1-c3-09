package com.madfinder.server.repository;

/**
 * collect_queue 접근 계층. (담당: KJH — 쿼리 전부 여기에)
 * 주요: status=pending LIMIT n(B2 소비), INSERT IGNORE 성격의 등록
 * TODO(KJH): JpaRepository 상속으로 전환 후 쿼리 메서드/@Query 작성.
 */
public interface CollectQueueRepository {
}
