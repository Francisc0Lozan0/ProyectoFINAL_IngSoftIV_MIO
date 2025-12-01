package com.sitm.mio.repository;

import com.sitm.mio.entity.PerformanceMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PerformanceMetricRepository extends JpaRepository<PerformanceMetric, Long> {
    
    List<PerformanceMetric> findByTestLabel(String testLabel);
    
    List<PerformanceMetric> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT p FROM PerformanceMetric p ORDER BY p.throughputDps DESC")
    List<PerformanceMetric> findAllOrderedByThroughput();
    
    @Query("SELECT AVG(p.throughputDps) FROM PerformanceMetric p WHERE p.testLabel = :testLabel")
    Double getAverageThroughputByTestLabel(String testLabel);
}
