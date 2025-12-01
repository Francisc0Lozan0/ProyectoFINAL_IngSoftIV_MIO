package com.sitm.mio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "cutoff_analysis", indexes = {
    @Index(name = "idx_scale", columnList = "scale"),
    @Index(name = "idx_timestamp_cutoff", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoffAnalysis {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "scale", nullable = false, length = 50)
    private String scale;
    
    @Column(name = "workers", nullable = false)
    private Integer workers;
    
    @Column(name = "batches", nullable = false)
    private Integer batches;
    
    @Column(name = "processing_time_ms", nullable = false)
    private Long processingTimeMs;
    
    @Column(name = "throughput_dps", nullable = false)
    private Double throughputDps;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        createdAt = LocalDateTime.now();
    }
}
