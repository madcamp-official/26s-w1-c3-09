package com.madfinder.server.repository;

import com.madfinder.server.entity.ChartSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** chart_snapshot 접근 계층. 차트 데이터는 배치(b1)가 쓰고 서버는 읽음. */
public interface ChartSnapshotRepository extends JpaRepository<ChartSnapshot, ChartSnapshot.Pk> {

    List<ChartSnapshot> findBySortIdOrderByChartRankAsc(String sortId);
}
