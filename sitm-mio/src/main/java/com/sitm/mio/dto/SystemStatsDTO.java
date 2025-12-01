package com.sitm.mio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para estadísticas del sistema
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatsDTO {
    private Integer activeWorkers;
    private Long totalRecordsProcessed;
    private Long totalVelocityRecords;
    private Double avgVelocityKmh;
    private Double maxVelocityKmh;
    private LocalDateTime lastProcessingTime;
    private String systemStatus;
}
