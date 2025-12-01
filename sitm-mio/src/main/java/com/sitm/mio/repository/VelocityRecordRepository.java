package com.sitm.mio.repository;

import com.sitm.mio.entity.VelocityRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VelocityRecordRepository extends JpaRepository<VelocityRecord, Long> {
    
    // Consultas por test label
    List<VelocityRecord> findByTestLabel(String testLabel);
    
    // Consultas por lineId
    List<VelocityRecord> findByLineId(String lineId);
    
    // Consultas por arcId
    List<VelocityRecord> findByArcId(String arcId);
    
    // Consultas combinadas
    List<VelocityRecord> findByTestLabelAndLineId(String testLabel, String lineId);
    
    List<VelocityRecord> findByTestLabelAndArcId(String testLabel, String arcId);
    
    // Consultas por rango de tiempo
    List<VelocityRecord> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    // Top velocidades por test
    @Query("SELECT v FROM VelocityRecord v WHERE v.testLabel = :testLabel ORDER BY v.velocityMs DESC")
    List<VelocityRecord> findTopVelocitiesByTestLabel(@Param("testLabel") String testLabel);
    
    // Líneas distintas
    @Query("SELECT DISTINCT v.lineId FROM VelocityRecord v WHERE v.lineId IS NOT NULL")
    List<String> findDistinctLineIds();
}
