package com.madfinder.server.repository;

import com.madfinder.server.entity.GroupCursor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** group_cursors 접근 계층. 배치(b4 팬 수집)의 커서 관리 — 서버는 읽기 위주. */
public interface GroupCursorRepository extends JpaRepository<GroupCursor, Long> {

    List<GroupCursor> findByCollectionStatus(String collectionStatus);
}
