package com.sitm.mio.repository;

import com.sitm.mio.entity.SummaryStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SummaryStatsRepository extends JpaRepository<SummaryStats, Long> {
    
    List<SummaryStats> findByTestLabel(String testLabel);
    
    Optional<SummaryStats> findTopByTestLabelOrderByTimestampDesc(String testLabel);
    
    List<SummaryStats> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
}
