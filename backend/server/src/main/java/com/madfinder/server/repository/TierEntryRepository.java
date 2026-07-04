package com.madfinder.server.repository;

import com.madfinder.server.entity.TierEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** tier_entries 접근 계층. 유저당 1세트 — 저장은 전체 삭제 후 재삽입. */
public interface TierEntryRepository extends JpaRepository<TierEntry, TierEntry.Pk> {

    List<TierEntry> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
