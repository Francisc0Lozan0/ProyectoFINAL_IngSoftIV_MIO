package com.sitm.mio.repository;

import com.sitm.mio.entity.CutoffAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CutoffAnalysisRepository extends JpaRepository<CutoffAnalysis, Long> {
    
    List<CutoffAnalysis> findByScale(String scale);
    
    List<CutoffAnalysis> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    List<CutoffAnalysis> findAllByOrderByScaleAscWorkersAsc();
}
