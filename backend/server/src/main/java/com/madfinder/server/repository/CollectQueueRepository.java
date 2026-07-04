package com.madfinder.server.repository;

import com.madfinder.server.entity.CollectQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** collect_queue 접근 계층. 서버는 등록(INSERT), 배치(b2)가 소비. */
public interface CollectQueueRepository extends JpaRepository<CollectQueue, Long> {

    List<CollectQueue> findByStatusOrderByRequestedAtAsc(String status, Pageable pageable);
}
